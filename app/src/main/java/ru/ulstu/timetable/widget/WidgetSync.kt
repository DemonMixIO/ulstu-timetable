package ru.ulstu.timetable.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ru.ulstu.timetable.Constants
import ru.ulstu.timetable.work.SyncWorker
import java.util.concurrent.TimeUnit

/** Постановка фоновой синхронизации расписания. */
object WidgetSync {

    private const val ONE_SHOT = "timetable_sync_now"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Обновить виджеты прямо сейчас (нажатие кнопки или изменение настроек). */
    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Периодическая синхронизация: виджеты и напоминания должны получать
     * свежее расписание, даже если приложение не открывали.
     */
    fun enqueuePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                Constants.SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(Constants.SYNC_WORK_NAME)
    }
}
