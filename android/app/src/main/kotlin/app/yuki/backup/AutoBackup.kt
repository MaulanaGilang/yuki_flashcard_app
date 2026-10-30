package app.yuki.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yuki.YukiApp
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Automatic weekly backup (ARCHITECTURE §2.9) into a folder you picked once (Storage Access
 * Framework tree, permission persisted). Keeps the newest [KEEP] automatic backups there.
 */
object AutoBackup {
  private const val WORK = "auto-backup"
  const val PREFIX = "yuki-auto-"
  private const val KEEP = 4

  fun schedule(context: Context, enabled: Boolean) {
    val wm = WorkManager.getInstance(context)
    if (!enabled) {
      wm.cancelUniqueWork(WORK)
      return
    }
    val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
      .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
      .setInitialDelay(1, TimeUnit.HOURS)
      .build()
    wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
  }

  /** Writes one backup into the tree now. Returns the number of cards saved. */
  suspend fun runOnce(context: Context, tree: Uri): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val graph = (context.applicationContext as YukiApp).graph
    val resolver = context.contentResolver
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val name = graph.backup.suggestedFileName().replace("yuki-", PREFIX)
    val target = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
      ?: error("couldn't create $name")
    val manifest = graph.backup.export(target)
    graph.settings.setLastAutoBackup(Instant.now())
    prune(context, tree)
    manifest.cards
  }

  private fun prune(context: Context, tree: Uri) {
    val resolver = context.contentResolver
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val autos = mutableListOf<Pair<String, String>>()
    resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
      while (c.moveToNext()) {
        val name = c.getString(1) ?: continue
        if (name.startsWith(PREFIX)) autos += c.getString(0) to name
      }
    }
    // Names embed yyyyMMdd-HHmm, so name order is age order.
    autos.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
      runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
    }
  }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val graph = (applicationContext as YukiApp).graph
    val folder = graph.settings.autoBackupFolder() ?: return Result.success()
    return try {
      AutoBackup.runOnce(applicationContext, Uri.parse(folder))
      Result.success()
    } catch (_: SecurityException) {
      // Folder access was revoked: stop trying until it's picked again in Settings.
      Result.failure()
    } catch (_: Exception) {
      if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
  }
}
