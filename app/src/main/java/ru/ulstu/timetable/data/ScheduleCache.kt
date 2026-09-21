package ru.ulstu.timetable.data

import org.json.JSONArray
import org.json.JSONObject
import ru.ulstu.timetable.model.DaySchedule
import ru.ulstu.timetable.model.Lesson
import ru.ulstu.timetable.model.Schedule
import ru.ulstu.timetable.model.ScheduleKind
import ru.ulstu.timetable.model.WeekSchedule
import java.time.LocalDate

/** Сохранение разобранного расписания, чтобы виджеты работали без сети. */
object ScheduleCache {

    fun toJson(schedule: Schedule): String {
        val root = JSONObject()
        root.put("title", schedule.title)
        root.put("kind", schedule.kind.name)
        root.put("url", schedule.sourceUrl)
        root.put("updatedAt", schedule.updatedAt)
        root.put("bells", JSONArray(schedule.bellTimes))

        val weeksJson = JSONArray()
        for (week in schedule.weeks) {
            val wj = JSONObject()
            wj.put("number", week.number)
            wj.put("start", week.start.toString())
            wj.put("end", week.end.toString())

            val daysJson = JSONArray()
            for (day in week.days) {
                val dj = JSONObject()
                dj.put("date", day.date.toString())
                dj.put("label", day.label)

                val lessonsJson = JSONArray()
                for (lesson in day.lessons) {
                    if (lesson == null || lesson.isEmpty) {
                        lessonsJson.put(JSONObject.NULL)
                    } else {
                        lessonsJson.put(
                            JSONObject()
                                .put("type", lesson.type)
                                .put("subject", lesson.subject)
                                .put("teacher", lesson.teacher)
                                .put("room", lesson.room)
                                .put("group", lesson.group)
                                .put("subgroup", lesson.subgroup)
                                .put("raw", lesson.raw)
                        )
                    }
                }
                dj.put("lessons", lessonsJson)
                daysJson.put(dj)
            }
            wj.put("days", daysJson)
            weeksJson.put(wj)
        }
        root.put("weeks", weeksJson)
        return root.toString()
    }

    fun fromJson(json: String): Schedule? {
        return try {
        val root = JSONObject(json)
        val weeksJson = root.optJSONArray("weeks") ?: JSONArray()
        val weeks = ArrayList<WeekSchedule>()

        for (i in 0 until weeksJson.length()) {
            val wj = weeksJson.optJSONObject(i) ?: continue
            val daysJson = wj.optJSONArray("days") ?: JSONArray()
            val days = ArrayList<DaySchedule>()

            for (d in 0 until daysJson.length()) {
                val dj = daysJson.optJSONObject(d) ?: continue
                val date = parseDate(dj.optString("date")) ?: continue
                val lessonsJson = dj.optJSONArray("lessons") ?: JSONArray()
                val lessons = ArrayList<Lesson?>(lessonsJson.length())

                for (l in 0 until lessonsJson.length()) {
                    if (lessonsJson.isNull(l)) {
                        lessons += null
                    } else {
                        val lj = lessonsJson.optJSONObject(l) ?: continue
                        lessons += Lesson(
                            type = lj.optString("type"),
                            subject = lj.optString("subject"),
                            teacher = lj.optString("teacher"),
                            room = lj.optString("room"),
                            group = lj.optString("group"),
                            subgroup = lj.optString("subgroup"),
                            raw = lj.optString("raw")
                        )
                    }
                }
                days += DaySchedule(date, dj.optString("label"), lessons)
            }

            val start = parseDate(wj.optString("start")) ?: days.firstOrNull()?.date ?: continue
            val end = parseDate(wj.optString("end")) ?: days.lastOrNull()?.date ?: start
            weeks += WeekSchedule(wj.optInt("number", i + 1), start, end, days)
        }

        if (weeks.isEmpty()) return null

        val bellsJson = root.optJSONArray("bells") ?: JSONArray()
        val bells = ArrayList<String>(bellsJson.length())
        for (i in 0 until bellsJson.length()) bells += bellsJson.optString(i)

        return Schedule(
            title = root.optString("title", "Расписание"),
            kind = runCatching { ScheduleKind.valueOf(root.optString("kind")) }
                .getOrDefault(ScheduleKind.UNKNOWN),
            weeks = weeks,
            bellTimes = bells,
            sourceUrl = root.optString("url"),
            updatedAt = root.optLong("updatedAt", 0L)
        )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDate(value: String?): LocalDate? = try {
        if (value.isNullOrBlank()) null else LocalDate.parse(value)
    } catch (e: Exception) {
        null
    }
}
