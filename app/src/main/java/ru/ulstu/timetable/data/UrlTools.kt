package ru.ulstu.timetable.data

import java.net.IDN

/**
 * Сайт живёт в кириллическом домене, а HttpURLConnection не умеет IDN,
 * поэтому все сетевые адреса приводим к punycode-виду.
 */
object UrlTools {

    private val HOST_RE = Regex("""^(https?://)([^/:?#]+)(.*)$""", RegexOption.IGNORE_CASE)

    /** Разделы сайта: путь, человеческое название. */
    val SECTIONS: List<Section> = listOf(
        Section("student", "ФИСТ, ГФ"),
        Section("mashfak", "МФ, РТФ, ЭФ, ИФМИ"),
        Section("itau", "ИАТУ, ИЭФ, ЗВФ"),
        Section("kei", "КЭИ"),
        Section("sf", "СФ"),
        Section("prepod", "Преподаватели")
    )

    data class Section(val path: String, val title: String)

    fun toAscii(url: String): String {
        val m = HOST_RE.find(url) ?: return url
        val (scheme, host, rest) = m.destructured
        val ascii = try {
            IDN.toASCII(host)
        } catch (e: Exception) {
            host
        }
        return scheme + ascii + rest
    }

    /** Хост в punycode и нижнем регистре — чтобы «житков.рф» и «xn--b1ahgiuw.xn--p1ai» совпадали. */
    fun host(url: String): String = try {
        Regex("""^https?://([^/:?#]+)""", RegexOption.IGNORE_CASE)
            .find(toAscii(url))
            ?.groupValues?.get(1)?.lowercase() ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Ссылка ведёт на тот же сайт расписания? */
    fun isSameSite(url: String, homeUrl: String): Boolean {
        val h = host(url)
        if (h.isEmpty()) return false
        return h == host(homeUrl) || h.endsWith(".xn--b1ahgiuw.xn--p1ai")
    }

    /** Страница группы: /student/10.html, /mashfak/21.html, ... */
    fun isGroupPage(url: String): Boolean =
        Regex("""/(student|mashfak|itau|kei|sf)/[^/]+\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /** Страница преподавателя: /prepod/m1.html */
    fun isTeacherPage(url: String): Boolean =
        Regex("""/prepod/[^/]+\.html?$""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    fun isSchedulePage(url: String): Boolean = isGroupPage(url) || isTeacherPage(url)

    /** Страница выбора группы/преподавателя: /student/, /prepod/, /mashfak/ и т.п. */
    fun isSectionIndex(url: String): Boolean =
        Regex("""/(student|mashfak|itau|kei|sf|prepod)/?$""", RegexOption.IGNORE_CASE)
            .containsMatchIn(url)

    /** Главная страница сайта (без пути). */
    fun isHome(url: String): Boolean =
        url.substringAfter("://", "")
            .substringAfter('/', "")
            .substringBefore('?')
            .substringBefore('#')
            .isBlank()

    /** «/student/10.html» -> «10.html» — короткое имя страницы. */
    fun fileName(url: String): String = url.substringAfterLast('/').substringBefore('?')

    fun displayHost(url: String): String = try {
        java.net.URL(toAscii(url)).host
    } catch (e: Exception) {
        url
    }
}
