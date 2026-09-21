package ru.ulstu.timetable.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Каталог групп и преподавателей.
 *
 * На сайте нет поиска: чтобы найти свою группу, нужно угадать раздел и курс.
 * Здесь все разделы скачиваются один раз, разбираются в плоский список и
 * кэшируются, поэтому в приложении есть поиск по названию.
 */
object GroupCatalog {

    data class Entry(
        val name: String,
        val url: String,
        val section: String,
        val course: Int
    )

    private val COURSE_RE = Regex("""(\d+)\s*курс""")

    suspend fun fetch(
        repository: ScheduleRepository,
        homeUrl: String
    ): List<Entry> = coroutineScope {
        UrlTools.SECTIONS.map { section ->
            async(Dispatchers.IO) {
                val base = homeUrl.trimEnd('/') + "/" + section.path.trim('/') + "/"
                repository.fetchHtml(base)
                    .getOrNull()
                    ?.let { parse(it, base, section.title) }
                    ?: emptyList()
            }
        }.awaitAll().flatten().distinctBy { it.url }
    }

    private fun parse(html: String, baseUrl: String, sectionTitle: String): List<Entry> {
        val doc = Jsoup.parse(html)

        // В шапке таблицы указаны курсы — по номеру столбца определяем курс группы.
        val courseByColumn = HashMap<Int, Int>()
        doc.selectFirst("table")
            ?.select("tr")
            ?.firstOrNull()
            ?.children()
            ?.forEachIndexed { index, cell ->
                COURSE_RE.find(cell.text())?.let { courseByColumn[index] = it.groupValues[1].toInt() }
            }

        val out = ArrayList<Entry>()
        for (link in doc.select("a[href]")) {
            val href = link.attr("href").trim()
            if (!href.endsWith(".html", ignoreCase = true)) continue
            if (href.startsWith("http", ignoreCase = true) || href.startsWith("//")) continue
            val name = link.text().replace('\u00a0', ' ').trim()
            if (name.isEmpty()) continue

            val column = link.parents()
                .firstOrNull { it.tagName() == "td" }
                ?.elementSiblingIndex() ?: -1

            out += Entry(
                name = name,
                url = baseUrl + href.removePrefix("./"),
                section = sectionTitle,
                course = courseByColumn[column] ?: 0
            )
        }
        return out
    }

    fun toJson(list: List<Entry>): String {
        val array = JSONArray()
        for (entry in list) {
            array.put(
                JSONObject()
                    .put("n", entry.name)
                    .put("u", entry.url)
                    .put("s", entry.section)
                    .put("c", entry.course)
            )
        }
        return array.toString()
    }

    fun fromJson(json: String): List<Entry> = try {
        val array = JSONArray(json)
        val out = ArrayList<Entry>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("u")
            if (url.isBlank()) continue
            out += Entry(
                name = obj.optString("n"),
                url = url,
                section = obj.optString("s"),
                course = obj.optInt("c", 0)
            )
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}
