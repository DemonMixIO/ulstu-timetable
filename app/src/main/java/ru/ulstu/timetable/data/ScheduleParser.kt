package ru.ulstu.timetable.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import ru.ulstu.timetable.model.DaySchedule
import ru.ulstu.timetable.model.Lesson
import ru.ulstu.timetable.model.Schedule
import ru.ulstu.timetable.model.ScheduleKind
import ru.ulstu.timetable.model.WeekSchedule
import java.time.LocalDate

/**
 * Разбор страниц сайта расписания.
 *
 * Страницы — это HTML, сгенерированный Microsoft Word 97, без единого класса или id,
 * поэтому разбор опирается на содержимое ячеек:
 *
 *  - строка «Пары  | 1-я | 2-я | ...»     — заголовок,
 *  - строка «Время | 08:30–09:50 | ...»   — время звонков,
 *  - строки «Пнд, 14.09.2026» и далее     — учебные дни.
 *
 * На странице обычно две таблицы: текущая неделя и следующая, поэтому парсер
 * возвращает обе — так виджет «следующая пара» работает и на границе недель.
 */
object ScheduleParser {

    private val WEEK_RE = Regex(
        """Неделя:\s*(\d+)\s*-?\s*я?\s*\(\s*(\d{2})\.(\d{2})\.(\d{4})\s*[–\-—]\s*(\d{2})\.(\d{2})\.(\d{4})"""
    )
    private val DAY_RE = Regex("""^\s*([А-Яа-яЁё]{3})\s*,\s*(\d{2})\.(\d{2})\.(\d{4})""")
    private val TIME_RE = Regex("""\d{1,2}:\d{2}\s*[–\-—]\s*\d{1,2}:\d{2}""")
    private val TITLE_RE = Regex(
        """(?:учебной\s+группы|преподавателя)\s*:[\s\S]{0,80}?<FONT[^>]*>\s*([^<]+?)\s*<BR>""",
        RegexOption.IGNORE_CASE
    )
    private val TABLE_END_RE = Regex("""(?i)</TABLE\s*>""")
    private val BR_RE = Regex("""(?i)<br\s*/?>""")
    private val TAG_RE = Regex("""<[^>]*>""")
    private val WS_RE = Regex("""\s+""")

    /** Слова, которыми на сайте обозначают вид занятия. */
    private val TYPE_WORDS = setOf(
        "лек", "пр", "лаб", "сем", "конс", "экз", "зач", "диф", "рубеж", "курс",
        "нир", "вкр", "преддипл", "производ", "учеб", "гос", "практика", "стажировка", "воен"
    )

    private val TEACHER_RE = Regex("""^[А-ЯЁ][а-яё\-]+(\s*[А-ЯЁ]\.?){1,3}$""")
    private val GROUP_RE = Regex("""^[A-Za-zА-Яа-яЁё][A-Za-zА-Яа-яЁё0-9\s,.\-]*-\d+[а-яА-ЯёЁ]?$""")

    /** Аудитория почти всегда выглядит как «3-308», «2-СЗ», «10-акт.зал». */
    private val ROOM_NUMBER_RE = Regex("""^\d{1,2}\s*[-–—]\s*\S""")

    /** Короткие обозначения аудиторий без номера. */
    private val ROOM_SHORT_WORDS = setOf(
        "сз", "зал", "ауд", "басс", "стад", "библ", "спортзал", "коворк",
        "бассейн", "стадион", "библиотека", "коворкинг", "актзал"
    )

    fun parse(html: String, sourceUrl: String, updatedAt: Long = System.currentTimeMillis()): Schedule? {
        if (html.isBlank()) return null

        val kind = when {
            html.contains("учебной группы", ignoreCase = true) -> ScheduleKind.STUDENT
            html.contains("преподавателя", ignoreCase = true) -> ScheduleKind.TEACHER
            else -> ScheduleKind.UNKNOWN
        }

        val title = extractTitle(html)
        val weeks = ArrayList<WeekSchedule>()
        var bellTimes: List<String> = emptyList()
        var index = 0

        for ((header, tableHtml) in tableChunks(html)) {
            index++
            val parsed = parseTable(Jsoup.parseBodyFragment(tableHtml)) ?: continue
            if (parsed.days.isEmpty()) continue
            if (bellTimes.isEmpty()) bellTimes = parsed.times

            val wm = WEEK_RE.findAll(header).lastOrNull()
            val week = if (wm != null) {
                val start = safeDate(wm.groupValues[2], wm.groupValues[3], wm.groupValues[4])
                val end = safeDate(wm.groupValues[5], wm.groupValues[6], wm.groupValues[7])
                WeekSchedule(
                    number = wm.groupValues[1].toIntOrNull() ?: index,
                    start = start ?: parsed.days.first().date,
                    end = end ?: parsed.days.last().date,
                    days = parsed.days
                )
            } else {
                WeekSchedule(
                    number = index,
                    start = parsed.days.first().date,
                    end = parsed.days.last().date,
                    days = parsed.days
                )
            }
            weeks += week
        }

        if (weeks.isEmpty()) return null

        return Schedule(
            title = title ?: fallbackTitle(sourceUrl),
            kind = kind,
            weeks = weeks,
            bellTimes = bellTimes,
            sourceUrl = sourceUrl,
            updatedAt = updatedAt
        )
    }

    // --- Внутреннее ---------------------------------------------------------

    private class ParsedTable(val times: List<String>, val days: List<DaySchedule>)

    /**
     * Режет документ по таблицам. Возвращает пары «текст перед таблицей» и «сама таблица»,
     * потому что заголовок недели лежит в тексте между таблицами.
     */
    private fun tableChunks(html: String): List<Pair<String, String>> {
        val parts = html.split(Regex("(?i)<TABLE"))
        val out = ArrayList<Pair<String, String>>()
        for (i in 1 until parts.size) {
            val header = parts[i - 1]
            val end = TABLE_END_RE.find(parts[i])
            val body = if (end != null) {
                "<TABLE" + parts[i].substring(0, end.range.last + 1)
            } else {
                "<TABLE" + parts[i]
            }
            out += header to body
        }
        return out
    }

    private fun extractTitle(html: String): String? {
        val m = TITLE_RE.find(html) ?: return null
        val raw = m.groupValues[1]
            .replace('\u00a0', ' ')
            .replace(WS_RE, " ")
            .trim()
        return raw.ifBlank { null }
    }

    private fun fallbackTitle(sourceUrl: String): String =
        UrlTools.fileName(sourceUrl).substringBefore('.').ifBlank { "Расписание" }

    private fun safeDate(day: String, month: String, year: String): LocalDate? = try {
        LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    } catch (e: Exception) {
        null
    }

    // toList() обязателен: у jsoup-коллекции Elements есть свой метод filter(NodeFilter),
    // который перекрывает стандартный filter {} из Kotlin.
    private fun cellsOf(row: Element): List<Element> =
        row.children().toList().filter { it.tagName() == "td" || it.tagName() == "th" }

    private fun parseTable(doc: org.jsoup.nodes.Document): ParsedTable? {
        val rows = doc.select("tr")
        if (rows.isEmpty()) return null

        var times: List<String> = emptyList()
        var timesRowIndex = -1

        rows.forEachIndexed { i, row ->
            if (timesRowIndex >= 0) return@forEachIndexed
            val cells = cellsOf(row)
            if (cells.isEmpty()) return@forEachIndexed
            val first = cells[0].text().trim()
            if (first.startsWith("Время", ignoreCase = true)) {
                times = cells.drop(1).map { normalizeTime(it.text()) }
                timesRowIndex = i
            }
        }

        // Если строку «Время» найти не удалось, ориентируемся на ширину первой строки.
        val pairCount = when {
            times.isNotEmpty() -> times.size
            rows.isNotEmpty() -> (cellsOf(rows[0]).size - 1).coerceAtLeast(0)
            else -> 0
        }
        if (pairCount <= 0) return null

        val days = ArrayList<DaySchedule>()
        val startFrom = if (timesRowIndex >= 0) timesRowIndex + 1 else 0
        for (i in startFrom until rows.size) {
            val cells = cellsOf(rows[i])
            if (cells.size < 2) continue
            val first = cells[0].text().replace('\u00a0', ' ').trim()
            val m = DAY_RE.find(first) ?: continue
            val date = safeDate(m.groupValues[2], m.groupValues[3], m.groupValues[4]) ?: continue

            val lessons = ArrayList<Lesson?>(pairCount)
            for (c in 1..pairCount) {
                val cell = cells.getOrNull(c)
                lessons += if (cell == null) null else parseCell(cellLines(cell))
            }
            days += DaySchedule(date, m.groupValues[1], lessons)
        }

        return ParsedTable(times, days)
    }

    private fun normalizeTime(text: String): String {
        val m = TIME_RE.find(text) ?: return text.replace('\u00a0', ' ').trim()
        return m.value.replace(Regex("""[–\-—]"""), "–")
    }

    /** Текст ячейки построчно: <br> становится переводом строки. */
    private fun cellLines(cell: Element): List<String> {
        var html = cell.html()
        html = html.replace(BR_RE, "\n")
        html = Parser.unescapeEntities(html, false)
        val text = html.replace(TAG_RE, " ")
        return text.split('\n', '\r')
            .map { it.replace('\u00a0', ' ').replace(WS_RE, " ").trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Раскладывает строки ячейки по смыслу, а не по позиции: на странице группы
     * порядок «вид, предмет, преподаватель, аудитория», а на странице преподавателя —
     * «группа, вид, предмет, аудитория». Из-за этого позиционный разбор ломается,
     * а классификация по содержимому работает для обеих схем.
     */
    private fun parseCell(lines: List<String>): Lesson? {
        if (lines.isEmpty()) return null

        var type = ""
        var teacher = ""
        var room = ""
        var group = ""
        var subgroup = ""
        val leftovers = ArrayList<String>()

        for (line in lines) {
            when {
                isSubgroupLine(line) -> subgroup = line
                isTypeLine(line) && type.isEmpty() -> type = line
                isTeacherLine(line) -> if (teacher.isEmpty()) teacher = line else leftovers += line
                isRoomLine(line) -> if (room.isEmpty()) room = line else leftovers += line
                isGroupLine(line) -> if (group.isEmpty()) group = line else leftovers += line
                else -> leftovers += line
            }
        }

        val subject = leftovers.joinToString(" ").trim()
        if (subject.isBlank() && type.isBlank() && room.isBlank() && group.isBlank()) return null

        return Lesson(
            type = type,
            subject = subject.ifBlank { leftovers.firstOrNull() ?: "" },
            teacher = teacher,
            room = room,
            group = group,
            subgroup = subgroup,
            raw = lines.joinToString(" · ")
        )
    }

    private fun isTypeLine(line: String): Boolean {
        val t = line.trim().trimEnd('.').lowercase()
        if (t.isEmpty() || t.length > 14) return false
        return t in TYPE_WORDS
    }

    private fun isSubgroupLine(line: String): Boolean {
        val t = line.lowercase()
        return t.contains("п/г") || t.contains("подгруп")
    }

    private fun isTeacherLine(line: String): Boolean {
        val t = line.trim()
        if (t.contains("кафедр", ignoreCase = true)) return true
        return TEACHER_RE.matches(t)
    }

    /**
     * Аудиторию определяем по структуре, а не по ключевым словам: поиск подстроки
     * вроде «спорт» ловил предмет «Элективные курсы по физической культуре и спорту»
     * и менял местами предмет с аудиторией.
     */
    private fun isRoomLine(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        if (ROOM_NUMBER_RE.containsMatchIn(t)) return true
        if (t.length > 14) return false
        val low = t.lowercase().trim('.', ',', ' ')
        return low in ROOM_SHORT_WORDS || low.endsWith(" зал")
    }

    private fun isGroupLine(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t[0].isDigit()) return false
        return GROUP_RE.matches(t)
    }
}
