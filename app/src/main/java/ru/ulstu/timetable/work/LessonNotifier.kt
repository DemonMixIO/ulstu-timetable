package ru.ulstu.timetable.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.ulstu.timetable.R
import ru.ulstu.timetable.data.Prefs
import ru.ulstu.timetable.model.LessonSlot
import ru.ulstu.timetable.model.Schedule
import ru.ulstu.timetable.model.ScheduleLogic
import ru.ulstu.timetable.ui.MainActivity
import ru.ulstu.timetable.widget.WidgetRenderer
import java.time.Duration
import java.time.LocalDateTime

/** Напоминание перед началом пары. */
object LessonNotifier {

    private const val CHANNEL_ID = "lessons"
    private const val NOTIFICATION_ID = 100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    /** Уведомляет, если до ближайшей пары осталось меньше заданного порога. */
    fun check(context: Context, prefs: Prefs, schedule: Schedule?) {
        if (!prefs.notificationsEnabled || schedule == null) return
        if (!areNotificationsAllowed(context)) return

        val now = LocalDateTime.now()
        val slot = ScheduleLogic.nextSlot(schedule, now, prefs.pairsExcludedFromWidget()) ?: return
        val start = slot.startDateTime() ?: return

        val minutes = Duration.between(now, start).toMinutes()
        if (minutes < 0 || minutes > prefs.notifyLeadMinutes) return

        // О паре предупреждаем один раз.
        val key = "${schedule.sourceUrl}|${slot.date}|${slot.pairIndex}"
        if (prefs.lastNotifiedKey == key) return
        prefs.lastNotifiedKey = key

        show(context, slot)
    }

    fun areNotificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun show(context: Context, slot: LessonSlot) {
        val text = buildString {
            append(WidgetRenderer.timeRange(slot))
            if (slot.lesson.subject.isNotBlank()) append(" · ").append(slot.lesson.subject)
            if (slot.lesson.room.isNotBlank()) append(" · ауд. ").append(slot.lesson.room)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 20, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
