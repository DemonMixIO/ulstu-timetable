package ru.ulstu.timetable

object Constants {

    /**
     * Домен кириллический, поэтому в коде он задан в punycode-виде,
     * а пользователю показывается в обычном.
     */
    const val HOME_URL = "https://timetable.xn--b1ahgiuw.xn--p1ai/"
    const val HOME_URL_PRETTY = "https://timetable.житков.рф/"

    const val ACTION_REFRESH_WIDGET = "ru.ulstu.timetable.action.REFRESH_WIDGET"

    /** Имя JS-моста, через который страница отдаёт разобранное расписание. */
    const val JS_BRIDGE = "AndroidTimetable"

    /** Периодическая синхронизация расписания для виджетов и уведомлений. */
    const val SYNC_WORK_NAME = "timetable_sync"

    /** Кэш страницы/расписания живёт в filesDir. */
    const val FILE_SCHEDULE_CACHE = "schedule_cache.json"
    const val FILE_PAGE_CACHE = "page_cache.html"
    const val FILE_GROUPS_CACHE = "groups_cache.json"
}
