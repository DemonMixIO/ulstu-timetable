package ru.ulstu.timetable

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import ru.ulstu.timetable.data.Prefs
import ru.ulstu.timetable.widget.WidgetRenderer
import ru.ulstu.timetable.widget.WidgetSync
import ru.ulstu.timetable.work.LessonNotifier

class TimetableApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val prefs = Prefs(this)

        // Тема применяется до создания первой Activity.
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)

        LessonNotifier.ensureChannel(this)

        // Расписание для виджетов и напоминаний обновляется и без запуска приложения.
        WidgetSync.enqueuePeriodic(this)
        WidgetRenderer.updateAll(this)
    }
}
