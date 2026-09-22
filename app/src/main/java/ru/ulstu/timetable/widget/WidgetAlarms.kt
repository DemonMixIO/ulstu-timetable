package ru.ulstu.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ru.ulstu.timetable.Constants
import ru.ulstu.timetable.data.ScheduleRepository
import ru.ulstu.timetable.model.Schedule
import ru.ulstu.timetable.model.ScheduleLogic
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Виджет с обратным отсчётом быстро устаревает, поэтому дополнительно
 * будим его ровно к началу и к концу ближайшей пары.
 * Будильник неточный — разрешение SCHEDULE_EXACT_ALARM не требуется.
 */
object WidgetAlarms {

    private const val REQUEST_CODE = 1001

    fun schedule(context: Context) {
        val schedule = ScheduleRepository(context).cachedSchedule() ?: return
        val now = LocalDateTime.now()
        val boundary = nextUpdate(schedule, now) ?: return

        val atMillis = boundary.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (atMillis <= System.currentTimeMillis() + 1_000L) return

        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.set(AlarmManager.RTC, atMillis, pendingRefresh(context)) }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pendingRefresh(context)) }
    }

    /**
     * Когда будить виджет в следующий раз.
     *
     * Пока пара идёт, в виджете тикают счётчики («идёт 25 мин · ещё 40 мин»),
     * поэтому обновляем его раз в минуту. В остальное время достаточно границ
     * пар — начала и конца занятий.
     */
    private fun nextUpdate(schedule: Schedule, now: LocalDateTime): LocalDateTime? {
        val slots = ScheduleLogic.slots(schedule)

        if (slots.any { ScheduleLogic.isNow(it, now) }) {
            return now.plusMinutes(1)
        }

        val candidates = ArrayList<LocalDateTime>()
        for (slot in slots) {
            slot.startDateTime()?.let { if (it.isAfter(now)) candidates += it }
            slot.endDateTime()?.let { if (it.isAfter(now)) candidates += it }
        }
        return candidates.minOrNull()
    }

    private fun pendingRefresh(context: Context): PendingIntent {
        val intent = Intent(context, NextLessonWidget::class.java).apply {
            action = Constants.ACTION_REFRESH_WIDGET
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
