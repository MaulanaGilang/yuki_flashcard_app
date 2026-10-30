package app.yuki.cloud

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yuki.YukiApp
import app.yuki.core.data.backup.BackupManager
import app.yuki.core.data.backup.BackupManifest
import app.yuki.core.data.backup.BackupPreview
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Online backup (ADR-7, amended 30 Oct 2026): study stays fully offline; when you leave the app
 * and you're online, the same `.yuki-backup` file as a manual backup is uploaded to your private
 * folder in Supabase Storage (`backups/<user id>/latest.yukibackup`, the one before kept as
 * `previous.yukibackup`). On a new phone you sign in and restore it.
 *
 * The sign-in session lives in this phone's SharedPreferences, never in progress.db, so it is not
 * part of the uploaded backup. The publishable key is meant to ship in apps; storage policies
 * only let a signed-in user touch their own folder.
 */
class CloudBackup(context: Context, private val backup: BackupManager) {
  private val app = context.applicationContext
  private val prefs = app.getSharedPreferences("cloud", Context.MODE_PRIVATE)
  private val lock = Mutex()

  data class State(val email: String?, val lastUpload: Instant?, val wifiOnly: Boolean)

  private val _state = MutableStateFlow(readState())
  val state: StateFlow<State> = _state

  val signedIn: Boolean get() = prefs.getString(REFRESH, null) != null

  sealed interface SignUpResult {
    data object SignedIn : SignUpResult
    /** Supabase sent a confirmation email; sign in after tapping its link. */
    data object ConfirmEmail : SignUpResult
  }

  class CloudException(message: String) : IOException(message)

  suspend fun signUp(email: String, password: String): SignUpResult = withContext(Dispatchers.IO) {
    val body = JSONObject().put("email", email.trim()).put("password", password)
    val json = JSONObject(request("POST", "$URL/auth/v1/signup", body = body.toString().toByteArray()).decodeToString())
    if (json.has("access_token")) {
      saveSession(json)
      SignUpResult.SignedIn
    } else {
      SignUpResult.ConfirmEmail
    }
  }

  suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
    val body = JSONObject().put("email", email.trim()).put("password", password)
    saveSession(JSONObject(request("POST", "$URL/auth/v1/token?grant_type=password", body = body.toString().toByteArray()).decodeToString()))
  }

  fun signOut() {
    prefs.edit().clear().putBoolean(WIFI_ONLY, _state.value.wifiOnly).apply()
    WorkManager.getInstance(app).cancelUniqueWork(WORK)
    _state.value = readState()
  }

  fun setWifiOnly(on: Boolean) {
    prefs.edit().putBoolean(WIFI_ONLY, on).apply()
    _state.value = readState()
  }

  /**
   * Queues an upload for when the phone is online (Wi-Fi only if chosen). Called when the app goes
   * to the background; skipped when nothing was studied since the last upload.
   */
  fun scheduleUpload(force: Boolean = false) {
    if (!signedIn) return
    val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
      .setInputData(androidx.work.workDataOf(FORCE to force))
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(if (_state.value.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
          .build(),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
      .build()
    WorkManager.getInstance(app).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
  }

  /** Uploads a fresh backup now, unless nothing changed since the last one. Returns null when skipped. */
  suspend fun upload(force: Boolean = false): BackupManifest? = lock.withLock {
    withContext(Dispatchers.IO) {
      val marker = backup.changeMarker()
      if (!force && marker == prefs.getString(MARKER, null)) return@withContext null
      val file = File(app.cacheDir, "cloud-upload.yukibackup").apply { delete() }
      val manifest = backup.export(Uri.fromFile(file))
      val uid = userId()
      // Keep the one before: previous <- latest, then upload the new latest. Missing files are fine.
      runCatching { request("DELETE", "$URL/storage/v1/object/$BUCKET", body = JSONObject().put("prefixes", JSONArray().put("$uid/$PREVIOUS")).toString().toByteArray(), auth = true) }
      runCatching {
        val move = JSONObject().put("bucketId", BUCKET).put("sourceKey", "$uid/$LATEST").put("destinationKey", "$uid/$PREVIOUS")
        request("POST", "$URL/storage/v1/object/move", body = move.toString().toByteArray(), auth = true)
      }
      request(
        "POST", "$URL/storage/v1/object/$BUCKET/$uid/$LATEST",
        body = file.readBytes(), auth = true, contentType = "application/zip", upsert = true,
      )
      file.delete()
      prefs.edit().putString(MARKER, marker).putString(LAST_UPLOAD, Instant.now().toString()).apply()
      _state.value = readState()
      manifest
    }
  }

  /** Downloads the newest online backup (or the one before, if the newest is missing) and checks it. */
  suspend fun download(): BackupPreview = withContext(Dispatchers.IO) {
    val uid = userId()
    val bytes = runCatching { request("GET", "$URL/storage/v1/object/authenticated/$BUCKET/$uid/$LATEST", auth = true) }
      .recoverCatching { request("GET", "$URL/storage/v1/object/authenticated/$BUCKET/$uid/$PREVIOUS", auth = true) }
      .getOrElse { throw CloudException("No online backup yet for this account.") }
    val file = File(app.cacheDir, "cloud-download.yukibackup").apply { writeBytes(bytes) }
    try {
      backup.inspect(Uri.fromFile(file))
    } finally {
      file.delete()
    }
  }

  // ---------- session ----------

  private fun readState() = State(
    email = prefs.getString(EMAIL, null),
    lastUpload = prefs.getString(LAST_UPLOAD, null)?.let { runCatching { Instant.parse(it) }.getOrNull() },
    wifiOnly = prefs.getBoolean(WIFI_ONLY, false),
  )

  private fun saveSession(json: JSONObject) {
    val user = json.getJSONObject("user")
    prefs.edit()
      .putString(ACCESS, json.getString("access_token"))
      .putString(REFRESH, json.getString("refresh_token"))
      .putLong(EXPIRES, Instant.now().epochSecond + json.optLong("expires_in", 3600))
      .putString(USER_ID, user.getString("id"))
      .putString(EMAIL, user.optString("email"))
      .apply()
    _state.value = readState()
  }

  private fun userId(): String = prefs.getString(USER_ID, null) ?: throw CloudException("Not signed in.")

  /** A valid access token, refreshed when it has less than a minute left. */
  private fun accessToken(): String {
    val refresh = prefs.getString(REFRESH, null) ?: throw CloudException("Not signed in.")
    if (Instant.now().epochSecond < prefs.getLong(EXPIRES, 0) - 60) return prefs.getString(ACCESS, null)!!
    val body = JSONObject().put("refresh_token", refresh).toString().toByteArray()
    saveSession(JSONObject(request("POST", "$URL/auth/v1/token?grant_type=refresh_token", body = body).decodeToString()))
    return prefs.getString(ACCESS, null)!!
  }

  // ---------- HTTP ----------

  private fun request(
    method: String,
    url: String,
    body: ByteArray? = null,
    auth: Boolean = false,
    contentType: String = "application/json",
    upsert: Boolean = false,
  ): ByteArray {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
      conn.requestMethod = method
      conn.connectTimeout = 15_000
      conn.readTimeout = 60_000
      conn.setRequestProperty("apikey", KEY)
      if (auth) conn.setRequestProperty("Authorization", "Bearer ${accessToken()}")
      if (upsert) conn.setRequestProperty("x-upsert", "true")
      if (body != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", contentType)
        conn.setFixedLengthStreamingMode(body.size)
        conn.outputStream.use { it.write(body) }
      }
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
      if (code !in 200..299) throw CloudException(errorMessage(code, bytes))
      return bytes
    } finally {
      conn.disconnect()
    }
  }

  private fun errorMessage(code: Int, bytes: ByteArray): String {
    val json = runCatching { JSONObject(bytes.decodeToString()) }.getOrNull()
    val text = json?.let { j -> listOf("msg", "error_description", "message", "error").firstNotNullOfOrNull { j.optString(it).ifBlank { null } } }
    return when {
      text?.contains("Invalid login credentials", ignoreCase = true) == true -> "Wrong email or password."
      text?.contains("Email not confirmed", ignoreCase = true) == true -> "Confirm your email first: tap the link Supabase sent you, then sign in."
      text?.contains("already registered", ignoreCase = true) == true -> "That email already has an account. Sign in instead."
      text != null -> text
      else -> "Server error ($code)."
    }
  }

  companion object {
    const val URL = "https://povhfxpwisfwwzhiqrje.supabase.co"
    private const val KEY = "sb_publishable_ZU7a-uwyIasLK50i7p1d5A_X2anGARc"
    private const val BUCKET = "backups"
    private const val LATEST = "latest.yukibackup"
    private const val PREVIOUS = "previous.yukibackup"
    private const val WORK = "cloud-backup"
    const val FORCE = "force"

    private const val ACCESS = "access_token"
    private const val REFRESH = "refresh_token"
    private const val EXPIRES = "expires_at"
    private const val USER_ID = "user_id"
    private const val EMAIL = "email"
    private const val LAST_UPLOAD = "last_upload"
    private const val MARKER = "uploaded_marker"
    private const val WIFI_ONLY = "wifi_only"
  }
}

class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val cloud = (applicationContext as YukiApp).graph.cloud
    if (!cloud.signedIn) return Result.success()
    return try {
      cloud.upload(force = inputData.getBoolean(CloudBackup.FORCE, false))
      Result.success()
    } catch (_: Exception) {
      if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
  }
}
