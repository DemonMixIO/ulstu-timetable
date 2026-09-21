package ru.ulstu.timetable.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.ulstu.timetable.data.Prefs
import ru.ulstu.timetable.data.ScheduleRepository
import ru.ulstu.timetable.widget.WidgetRenderer

/**
 * Обновляет расписание «моей группы» в фоне и после этого приводит в порядок
 * виджеты и напоминания. Работает и когда приложение закрыто.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = Prefs(context)
        val url = prefs.trackedUrl

        if (url.isBlank()) {
            WidgetRenderer.updateAll(context)
            return Result.success()
        }

        val repository = ScheduleRepository(context)
        val result = repository.refresh(url)
        val schedule = repository.cachedSchedule()

        WidgetRenderer.updateAll(context)
        LessonNotifier.check(context, prefs, schedule)

        return if (result.isSuccess) Result.success() else Result.retry()
    }
}
