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

        /** Тап по паре в расписании: пометить её необязательной или снять пометку. */
        fun onOptionalToggle(cellKey: String, optional: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPage(url: String, title: String, pairCount: Int, isSchedule: Boolean, html: String) {
        // Метод вызывается в потоке JavaScript, поэтому переходим в главный.
        main.post { listener.onPageReady(url, title, pairCount, isSchedule, html) }
    }

    @JavascriptInterface
    fun onOptionalToggle(cellKey: String, optional: Boolean) {
        main.post { listener.onOptionalToggle(cellKey, optional) }
    }
}
