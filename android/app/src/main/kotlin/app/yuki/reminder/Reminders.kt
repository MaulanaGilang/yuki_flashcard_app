package app.yuki.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.yuki.MainActivity
import app.yuki.R
import app.yuki.YukiApp
import app.yuki.core.model.YukiCoach
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

/**
 * Daily study reminder (PRD S13): an inexact alarm at the chosen time. The notification only
 * appears when cards are due and today's session hasn't happened yet. Rescheduled after every
 * firing, after a reboot and after app updates.
 */
object Reminders {
  const val CHANNEL = "study_reminder"
  private const val REQUEST = 1001
  private const val NOTIFICATION_ID = 1

  /** Schedules the next reminder at [time], or cancels it when [time] is null. */
  fun schedule(context: Context, time: LocalTime?) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    val pending = PendingIntent.getBroadcast(
      context, REQUEST, Intent(context, ReminderReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarms.cancel(pending)
    if (time == null) return
    val at = nextTrigger(ZonedDateTime.now(ZoneId.systemDefault()), time)
    // A 10-minute window needs no exact-alarm permission and is kind to the battery.
    alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), 10 * 60_000L, pending)
  }

  fun nextTrigger(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val today = now.with(time).withSecond(0).withNano(0)
    return if (today.isAfter(now)) today else today.plusDays(1)
  }

  fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val channel = NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
      description = "A nudge from Yuki when today's cards are waiting."
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  internal fun show(context: Context, title: String, text: String) {
    if (!canNotify(context)) return
    createChannel(context)
    val open = PendingIntent.getActivity(
      context, 0,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_stat_yuki)
      .setLargeIcon(ContextCompat.getDrawable(context, R.drawable.yuki_bust_wave)?.toBitmap(256, 256))
      .setColor(0xFFD63A22.toInt())
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(open)
      .setAutoCancel(true)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .build()
    try {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
      // Permission revoked between the check and the post: nothing to show.
    }
  }
}

class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext as YukiApp
    val pending = goAsync()
    app.graph.appScope.launch {
      try {
        val graph = app.graph
        val status = graph.progress.dayStatus()
        if (status.hasLines && !status.studiedToday && status.dueToday > 0) {
          val line = YukiCoach.line(status)
          val title = if (status.streak > 0) "🔥 ${status.streak}-day streak" else "Time for Yuki"
          Reminders.show(context, title, "${line.message} ${status.dueToday} cards are waiting.")
        }
        Reminders.schedule(context, graph.settings.reminderTime())
      } finally {
        pending.finish()
      }
    }
  }
}

/** Alarms don't survive a reboot or an app update: put the reminder back. */
class RescheduleReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext as YukiApp
    val pending = goAsync()
    app.graph.appScope.launch {
      try {
        Reminders.schedule(context, app.graph.settings.reminderTime())
      } finally {
        pending.finish()
      }
    }
  }
}
