package ru.ulstu.timetable.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ulstu.timetable.Constants
import ru.ulstu.timetable.model.Schedule
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Сеть и локальный кэш расписания.
 *
 * Кэш нужен виджетам и уведомлениям: они должны показывать пары,
 * даже когда приложение закрыто или пропал интернет.
 */
class ScheduleRepository(private val context: Context) {

    private val scheduleFile = File(context.filesDir, Constants.FILE_SCHEDULE_CACHE)
    private val pageFile = File(context.filesDir, Constants.FILE_PAGE_CACHE)

    // --- Сеть ---------------------------------------------------------------

    suspend fun fetchHtml(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { request(url) }
    }

    /** Скачивает страницу, разбирает и сохраняет в кэш. */
    suspend fun refresh(url: String): Result<Schedule> = fetchHtml(url).mapCatching { html ->
        // Фоновая синхронизация качает страницу как есть, поэтому фильтр подгрупп
        // применяется при разборе — иначе в виджет попадут занятия чужой подгруппы.
        val prefs = Prefs(context)
        val subgroup = if (prefs.subgroupEnabled) prefs.subgroup else 0
        val schedule = ScheduleParser.parse(html, url, subgroup)
            ?: throw IOException("Не удалось разобрать страницу расписания")
        saveSchedule(schedule)
        savePageHtml(url, html)
        schedule
    }

    private fun request(url: String): String {
        val ascii = UrlTools.toAscii(url)
        val conn = (URL(ascii).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val encoding = conn.contentEncoding?.lowercase().orEmpty()
            val raw = conn.inputStream
            val stream = if (encoding.contains("gzip")) GZIPInputStream(raw) else raw
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // --- Кэш расписания -----------------------------------------------------

    fun saveSchedule(schedule: Schedule) {
        runCatching { scheduleFile.writeText(ScheduleCache.toJson(schedule), Charsets.UTF_8) }
    }

    fun cachedSchedule(): Schedule? {
        if (!scheduleFile.exists()) return null
        return runCatching { ScheduleCache.fromJson(scheduleFile.readText(Charsets.UTF_8)) }.getOrNull()
    }

    /** Кэш страницы целиком — для показа расписания без интернета. */
    fun savePageHtml(url: String, html: String) {
        runCatching {
            // Первая строка — адрес, дальше — сама страница.
            pageFile.writeText(url + "\n" + html, Charsets.UTF_8)
        }
    }

    fun cachedPage(): Pair<String, String>? {
        if (!pageFile.exists()) return null
        return runCatching {
            val text = pageFile.readText(Charsets.UTF_8)
            val nl = text.indexOf('\n')
            if (nl <= 0) return null
            val url = text.substring(0, nl)
            val html = text.substring(nl + 1)
            if (html.isBlank()) null else url to html
        }.getOrNull()
    }

    /** Есть ли сохранённая копия именно этой страницы. */
    fun cachedPageFor(url: String): String? {
        val (cachedUrl, html) = cachedPage() ?: return null
        return if (cachedUrl == url) html else null
    }

    fun clear() {
        runCatching { scheduleFile.delete() }
        runCatching { pageFile.delete() }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36 TimetableApp/1.0"
    }
}
