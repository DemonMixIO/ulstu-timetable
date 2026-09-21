package ru.ulstu.timetable.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Одно занятие из ячейки таблицы расписания.
 *
 * На странице группы ячейка выглядит как «пр.<br>Предмет<br>Преподаватель<br>Аудитория»,
 * а на странице преподавателя порядок другой: «Группа<br>пр.<br>Предмет<br>Аудитория».
 * Парсер раскладывает строки по смыслу, поэтому обе схемы дают одну и ту же модель.
 */
data class Lesson(
    val type: String = "",
    val subject: String = "",
    val teacher: String = "",
    val room: String = "",
    val group: String = "",
    val subgroup: String = "",
    val raw: String = ""
) {
    val isEmpty: Boolean
        get() = subject.isBlank() && room.isBlank() && type.isBlank()

    /** «лек. · 3-231 · Иванов И И» — для второй строки в списках и виджетах. */
    fun subtitle(): String {
        val who = teacher.ifBlank { group }
        return listOf(type, room, who).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun typeCategory(): LessonType = LessonType.of(type)

    /** Номер подгруппы из «2-я п/г»; 0 — подгруппа не указана, занятие общее. */
    fun subgroupNumber(): Int =
        SUBGROUP_RE.find(subgroup)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    companion object {
        private val SUBGROUP_RE = Regex("""(\d)\s*-\s*я\s*п\s*/\s*г""", RegexOption.IGNORE_CASE)
    }
}

enum class LessonType {
    LECTURE, PRACTICE, LAB, OTHER;

    companion object {
        fun of(type: String): LessonType {
            val t = type.lowercase()
            return when {
                t.startsWith("лек") -> LECTURE
                t.startsWith("лаб") -> LAB
                t.startsWith("пр") -> PRACTICE
                else -> OTHER
            }
        }
    }
}

/** Учебный день: дата и список пар (индекс 0 соответствует 1-й паре). */
data class DaySchedule(
    val date: LocalDate,
    val label: String,
    val lessons: List<Lesson?>
) {
    val hasLessons: Boolean
        get() = lessons.any { it != null && !it.isEmpty }

    /** Пара по её номеру (1-я пара — это pairIndex = 1). */
    fun lessonAt(pairIndex: Int): Lesson? =
        lessons.getOrNull(pairIndex - 1)?.takeIf { !it.isEmpty }
}

/** Одна учебная неделя страницы (на странице их обычно две — текущая и следующая). */
data class WeekSchedule(
    val number: Int,
    val start: LocalDate,
    val end: LocalDate,
    val days: List<DaySchedule>
)

enum class ScheduleKind { STUDENT, TEACHER, UNKNOWN }

/** Разобранное расписание группы или преподавателя. */
data class Schedule(
    val title: String,
    val kind: ScheduleKind,
    val weeks: List<WeekSchedule>,
    val bellTimes: List<String>,
    val sourceUrl: String,
    val updatedAt: Long
) {
    val days: List<DaySchedule> get() = weeks.flatMap { it.days }

    fun day(date: LocalDate): DaySchedule? = days.firstOrNull { it.date == date }
}

/** Конкретная пара в конкретный день — то, из чего считается «следующая пара». */
data class LessonSlot(
    val date: LocalDate,
    val pairIndex: Int,
    val start: LocalTime?,
    val end: LocalTime?,
    val lesson: Lesson
) {
    fun startDateTime(): LocalDateTime? = start?.let { LocalDateTime.of(date, it) }

    fun endDateTime(): LocalDateTime? = end?.let { LocalDateTime.of(date, it) }

    /** Ключ ячейки для тегов — совпадает с ключом, который строит JS: «2026-09-14|3». */
    fun cellKey(): String = "$date|$pairIndex"
}

object ScheduleLogic {

    private val TIME_RANGE = Regex("""(\d{1,2}):(\d{2})\s*[–\-—]\s*(\d{1,2}):(\d{2})""")
    val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val DAY_SHORT = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val MONTH_GEN = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    /** Разбирает «08:30–09:50» в пару времён начала и конца. */
    fun parseTimeRange(text: String): Pair<LocalTime, LocalTime>? {
        val m = TIME_RANGE.find(text) ?: return null
        return try {
            val from = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            val to = LocalTime.of(m.groupValues[3].toInt(), m.groupValues[4].toInt())
            from to to
        } catch (e: Exception) {
            null
        }
    }

    fun dayShort(date: LocalDate): String = DAY_SHORT[date.dayOfWeek.value - 1]

    fun dateLabel(date: LocalDate): String =
        "${dayShort(date)}, ${date.dayOfMonth} ${MONTH_GEN[date.monthValue - 1]}"

    fun dateLabelShort(date: LocalDate): String =
        "%s, %02d.%02d".format(dayShort(date), date.dayOfMonth, date.monthValue)

    /** Все пары расписания в хронологическом порядке. */
    fun slots(schedule: Schedule): List<LessonSlot> {
        val out = ArrayList<LessonSlot>()
        for (week in schedule.weeks) {
            for (day in week.days) {
                day.lessons.forEachIndexed { index, lesson ->
                    if (lesson == null || lesson.isEmpty) return@forEachIndexed
                    val time = schedule.bellTimes.getOrNull(index)?.let { parseTimeRange(it) }
                    out += LessonSlot(day.date, index + 1, time?.first, time?.second, lesson)
                }
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.start ?: LocalTime.MAX }, { it.pairIndex }))
    }

    /** Пары на конкретную дату, отсортированные по номеру пары. */
    fun slotsOn(schedule: Schedule, date: LocalDate): List<LessonSlot> =
        slots(schedule).filter { it.date == date }

    /**
     * Ближайшая пара: первая, которая ещё не закончилась.
     * Если пара идёт прямо сейчас, вернётся именно она — так виджет полезнее.
     *
     * [exclude] отсеивает пары, которые пользователю показывать не нужно:
     * скрытые, помеченные необязательными и чужие подгруппы.
     */
    fun nextSlot(
        schedule: Schedule,
        now: LocalDateTime,
        exclude: (LessonSlot) -> Boolean = { false }
    ): LessonSlot? {
        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()
        return slots(schedule)
            .filter { !exclude(it) }
            .filter { it.date >= today }
            .firstOrNull { slot ->
                val end = slot.end ?: slot.start
                when {
                    end == null -> true
                    slot.date > today -> true
                    else -> !end.isBefore(nowTime)
                }
            }
    }

    /** Идёт ли пара прямо сейчас. */
    fun isNow(slot: LessonSlot, now: LocalDateTime): Boolean {
        if (slot.date != now.toLocalDate()) return false
        val start = slot.start ?: return false
        val end = slot.end ?: return false
        return !now.toLocalTime().isBefore(start) && now.toLocalTime().isBefore(end)
    }

    /** «через 1 ч 20 мин» / «идёт сейчас». */
    fun humanUntil(slot: LessonSlot, now: LocalDateTime): String {
        if (isNow(slot, now)) return "идёт сейчас"
        val start = slot.startDateTime() ?: return ""
        val minutes = java.time.Duration.between(now, start).toMinutes()
        if (minutes <= 0) return "идёт сейчас"
        val h = minutes / 60
        val m = minutes % 60
        val span = when {
            h > 0 && m > 0 -> "$h ч $m мин"
            h > 0 -> "$h ч"
            else -> "$m мин"
        }
        return "через $span"
    }

    /** «Сегодня», «Завтра» или «Пт, 25.09». */
    fun dayLabel(slot: LessonSlot, now: LocalDateTime): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), slot.date)
        return when (days) {
            0L -> "Сегодня"
            1L -> "Завтра"
            else -> dateLabelShort(slot.date)
        }
    }
}
