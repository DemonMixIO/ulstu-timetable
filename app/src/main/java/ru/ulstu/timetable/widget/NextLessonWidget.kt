package ru.ulstu.timetable.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import ru.ulstu.timetable.Constants

/** Виджет «Следующая пара». */
class NextLessonWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = WidgetRenderer.buildNext(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
        // Свежие данные подтянем в фоне, виджет обновится сам.
        WidgetSync.enqueueNow(context)
        WidgetAlarms.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Constants.ACTION_REFRESH_WIDGET) {
            WidgetRenderer.updateAll(context)
            WidgetSync.enqueueNow(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSync.enqueueNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetAlarms.cancel(context)
    }
}
