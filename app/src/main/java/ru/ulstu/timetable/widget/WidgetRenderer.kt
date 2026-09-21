package ru.ulstu.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.ulstu.timetable.Constants
import ru.ulstu.timetable.R
import ru.ulstu.timetable.data.Prefs
import ru.ulstu.timetable.data.ScheduleRepository
import ru.ulstu.timetable.model.LessonSlot
import ru.ulstu.timetable.model.ScheduleLogic
import ru.ulstu.timetable.ui.MainActivity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Собирает содержимое виджетов из локального кэша расписания. */
object WidgetRenderer {

    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        updateNext(context, manager)
        updateToday(context, manager)
        WidgetAlarms.schedule(context)
    }

    private fun updateNext(context: Context, manager: AppWidgetManager) {
        val ids = manager.getAppWidgetIds(ComponentName(context, NextLessonWidget::class.java))
        if (ids.isEmpty()) return
        val views = buildNext(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun updateToday(context: Context, manager: AppWidgetManager) {
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
        if (ids.isEmpty()) return
        val views = buildToday(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    // --- Виджет «Следующая пара» -------------------------------------------

    fun buildNext(context: Context): RemoteViews {
        val prefs = Prefs(context)
        val views = RemoteViews(context.packageName, R.layout.widget_next_lesson)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh(context))
        views.setTextViewText(
            R.id.widget_group,
            prefs.trackedTitle.ifBlank { context.getString(R.string.app_name) }
        )

        val schedule = ScheduleRepository(context).cachedSchedule()
        val now = LocalDateTime.now()

        if (schedule == null) {
            views.setTextViewText(R.id.widget_badge, "–")
            views.setTextViewText(R.id.widget_subject, context.getString(R.string.widget_no_group))
            views.setTextViewText(R.id.widget_details, context.getString(R.string.widget_no_data))
            views.setTextViewText(R.id.widget_when, "")
            views.setTextViewText(R.id.widget_countdown, "")
            return views
        }

        val slot = ScheduleLogic.nextSlot(schedule, now, prefs.pairsExcludedFromWidget())
        if (slot == null) {
            val hasAny = ScheduleLogic.slots(schedule).isNotEmpty()
            views.setTextViewText(R.id.widget_badge, "–")
            views.setTextViewText(R.id.widget_subject, context.getString(R.string.next_lesson_none_week))
            views.setTextViewText(
                R.id.widget_details,
                if (hasAny) updatedLabel(context, schedule.updatedAt)
                else context.getString(R.string.widget_no_data)
            )
            views.setTextViewText(R.id.widget_when, "")
            views.setTextViewText(R.id.widget_countdown, "")
            return views
        }

        views.setTextViewText(R.id.widget_badge, slot.pairIndex.toString())
        views.setTextViewText(R.id.widget_subject, slot.lesson.subject.ifBlank { slot.lesson.raw })
        views.setTextViewText(
            R.id.widget_details,
            slot.lesson.subtitle().ifBlank { updatedLabel(context, schedule.updatedAt) }
        )
        views.setTextViewText(
            R.id.widget_when,
            "${ScheduleLogic.dayLabel(slot, now)} · ${timeRange(slot)}"
        )
        views.setTextViewText(R.id.widget_countdown, ScheduleLogic.humanUntil(slot, now))
        return views
    }

    // --- Виджет «Пары на сегодня» ------------------------------------------

    fun buildToday(context: Context): RemoteViews {
        val prefs = Prefs(context)
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh(context))
        views.setTextViewText(
            R.id.widget_group,
            prefs.trackedTitle.ifBlank { context.getString(R.string.app_name) }
        )
        views.removeAllViews(R.id.widget_rows)

        val schedule = ScheduleRepository(context).cachedSchedule()
        val now = LocalDateTime.now()

        if (schedule == null) {
            views.setTextViewText(R.id.widget_date, "")
            views.addView(R.id.widget_rows, messageRow(context, R.string.widget_no_group))
            return views
        }

        val today = now.toLocalDate()
        views.setTextViewText(R.id.widget_date, ScheduleLogic.dateLabel(today))

        val slots = ScheduleLogic.slotsOn(schedule, today)
            .filter { it.pairIndex !in prefs.pairsExcludedFromWidget() }

        if (slots.isEmpty()) {
            views.addView(R.id.widget_rows, messageRow(context, R.string.lessons_none))
            return views
        }

        slots.take(MAX_ROWS).forEach { slot ->
            val row = RemoteViews(context.packageName, R.layout.item_widget_lesson)
            row.setTextViewText(R.id.row_badge, slot.pairIndex.toString())
            row.setTextViewText(R.id.row_subject, slot.lesson.subject.ifBlank { slot.lesson.raw })
            row.setTextViewText(R.id.row_details, slot.lesson.subtitle())
            row.setTextViewText(R.id.row_time, timeRange(slot))
            views.addView(R.id.widget_rows, row)
        }
        return views
    }

    // --- Вспомогательное ----------------------------------------------------

    private const val MAX_ROWS = 6

    private fun messageRow(context: Context, textRes: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.item_widget_message)
        row.setTextViewText(R.id.message_text, context.getString(textRes))
        return row
    }

    fun timeRange(slot: LessonSlot): String {
        val start = slot.start ?: return ""
        val end = slot.end
        return if (end == null) start.format(HHMM) else "${start.format(HHMM)}–${end.format(HHMM)}"
    }

    private fun updatedLabel(context: Context, updatedAt: Long): String {
        if (updatedAt <= 0L) return ""
        val time = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalTime()
        return context.getString(R.string.widget_updated, time.format(HHMM))
    }

    fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 10, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refresh(context: Context): PendingIntent {
        val intent = Intent(context, NextLessonWidget::class.java).apply {
            action = Constants.ACTION_REFRESH_WIDGET
        }
        return PendingIntent.getBroadcast(
            context, 11, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
