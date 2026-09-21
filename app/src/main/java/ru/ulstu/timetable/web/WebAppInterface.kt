package ru.ulstu.timetable.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Мост «страница → приложение».
 *
 * Страница сама отдаёт свой HTML, когда распознаёт в себе расписание.
 * Это надёжнее повторного скачивания: в виджет попадает ровно то,
 * что пользователь видит на экране, включая данные из кэша WebView.
 */
class WebAppInterface(private val listener: Listener) {

    interface Listener {
        fun onPageReady(
            url: String,
            title: String,
            pairCount: Int,
            isSchedule: Boolean,
            html: String
        )
    }

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPage(url: String, title: String, pairCount: Int, isSchedule: Boolean, html: String) {
        // Метод вызывается в потоке JavaScript, поэтому переходим в главный.
        main.post { listener.onPageReady(url, title, pairCount, isSchedule, html) }
    }
}
