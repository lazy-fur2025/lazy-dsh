package io.github.lazy.dsh.core.timetable

/**
 * The school timetable (济宁医学院 教务系统), kept as plain data so the tools, the cache and the
 * .ics export all agree on one shape. Nothing here touches Android: parsing, week maths, the
 * 作息 table and the calendar come from the JSON the system's own list endpoint returns.
 */
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One scheduled class: one row of `xsgrkbcx!getDataList.action`. */
@Serializable
data class Course(
    val course: String = "",
    /** 理论教学 / 实验教学 / 体育教学 … */
    val type: String = "",
    val teacher: String = "",
    val room: String = "",
    /** 1 = Monday … 7 = Sunday */
    val weekday: Int = 0,
    /** Small periods covered, e.g. [3, 4] for `jcdm=0304` */
    val periods: List<Int> = emptyList(),
    /** Week of the term (1-based) */
    val week: Int = 0,
    /** Real date, `YYYY-MM-DD` — the authoritative field; weekday/week follow from it */
    val date: String = "",
    /** 合班班级 */
    val klass: String = "",
    /** What happens in this class (sknrjj) */
    val note: String = "",
) {
    val startPeriod: Int get() = periods.firstOrNull() ?: 0
    val endPeriod: Int get() = periods.lastOrNull() ?: 0
    /** `3-4`, or `3` for a single period */
    val slot: String get() = if (periods.size <= 1) "$startPeriod" else "$startPeriod-$endPeriod"
}

/** 作息: the school has five 90-minute blocks, matching the only `jcdm` values (0102 … 1112). */
enum class Season(val id: String) {
    Winter("winter"), Summer("summer");

    companion object {
        fun of(id: String?): Season? = entries.firstOrNull { it.id == id }
    }
}

object Schedule {
    /** 冬季: 上午 08:00-11:20, 下午 14:00-17:20, 晚上 19:00-20:30. */
    private val WINTER = mapOf(
        1 to ("08:00" to "08:45"), 2 to ("08:45" to "09:30"),
        3 to ("09:50" to "10:35"), 4 to ("10:35" to "11:20"),
        6 to ("14:00" to "14:45"), 7 to ("14:45" to "15:30"),
        8 to ("15:50" to "16:35"), 9 to ("16:35" to "17:20"),
        11 to ("19:00" to "19:45"), 12 to ("19:45" to "20:30"),
    )

    /** 夏季: only the afternoon moves, 30 minutes later. */
    private val SUMMER = WINTER + mapOf(
        6 to ("14:30" to "15:15"), 7 to ("15:15" to "16:00"),
        8 to ("16:20" to "17:05"), 9 to ("17:05" to "17:50"),
    )

    fun table(season: Season): Map<Int, Pair<String, String>> = if (season == Season.Winter) WINTER else SUMMER

    fun start(season: Season, period: Int): String? = table(season)[period]?.first
    fun end(season: Season, period: Int): String? = table(season)[period]?.second

    /** `09:50-11:20` for [c], or null when a period is missing from the table. */
    fun span(season: Season, c: Course): String? {
        val s = start(season, c.startPeriod) ?: return null
        val e = end(season, c.endPeriod) ?: return null
        return "$s-$e"
    }
}

// ---------------------------------------------------------------- parsing

object TimetableParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Rows of the list endpoint: `{ total, rows: [ { kcmc, teaxms, jxcdmc, xq, jcdm, zc, pkrq, … } ] }`.
     * Anything else (a login page, an error page) throws [NotTimetableJson] so the tool can explain.
     */
    fun parse(body: String): List<Course> {
        val text = body.trim()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw NotTimetableJson(text.take(300))
        val rows = root["rows"] as? JsonArray
            ?: throw NotTimetableJson(text.take(300))
        return rows.mapNotNull { (it as? JsonObject)?.let(::course) }
    }

    private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun JsonObject.n(k: String): Int = s(k).toIntOrNull() ?: 0

    private fun course(o: JsonObject): Course? {
        val name = o.s("kcmc")
        if (name.isEmpty()) return null
        return Course(
            course = name,
            type = o.s("jxhjmc"),
            teacher = o.s("teaxms"),
            room = o.s("jxcdmc").replace('\\', '/'),
            weekday = o.n("xq"),
            periods = periodsOf(o.s("jcdm")),
            week = o.n("zc"),
            date = o.s("pkrq"),
            klass = o.s("jxbmc"),
            note = o.s("sknrjj"),
        )
    }

    /** `0304` -> [3, 4]; unknown shapes are dropped rather than guessed. */
    fun periodsOf(code: String): List<Int> = code.chunked(2).mapNotNull { it.toIntOrNull() }
}

/** The response was not the list endpoint's JSON (usually: the session is gone and this is the login page). */
class NotTimetableJson(val snippet: String) : Exception("not the timetable JSON")

// ---------------------------------------------------------------- weeks and dates

/** A term: its `xnxqdm` code and the Monday of week 1. */
@Serializable
data class Term(val id: String = "", val firstMonday: String = "") {
    val firstDay: LocalDate? get() = runCatching { LocalDate.parse(firstMonday) }.getOrNull()

    /** Week number of [date]; 0 before week 1. */
    fun weekOf(date: LocalDate): Int {
        val monday = firstDay ?: return 0
        val days = ChronoUnit.DAYS.between(monday, date)
        return if (days < 0) 0 else (days / 7).toInt() + 1
    }

    /** Monday of [week]. */
    fun mondayOf(week: Int): LocalDate? = firstDay?.plusWeeks((week - 1).toLong())
}

/** Week 1's Monday from the earliest scheduled date. */
fun firstMondayOf(courses: List<Course>): LocalDate? {
    val earliest = courses.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.minOrNull() ?: return null
    return earliest.minusDays((earliest.dayOfWeek.value - 1).toLong())
}

/** When to post a class reminder and when the class itself starts (epoch millis). */
data class ClassAlert(val course: Course, val alertAt: Long, val startsAt: Long) {
    val leadMinutes: Int get() = ((startsAt - alertAt) / 60_000L).toInt()
}

/**
 * The first class whose alert is still ahead of [after]; classes already under way are skipped, so a
 * reminder never fires for a class the phone slept through. Pure, so the alarm chain can be tested.
 */
fun nextClassAlert(
    courses: List<Course>,
    season: Season,
    leadMinutes: Int,
    after: Long,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): ClassAlert? = courses.mapNotNull { c ->
    val date = runCatching { LocalDate.parse(c.date) }.getOrNull() ?: return@mapNotNull null
    val time = Schedule.start(season, c.startPeriod)?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
        ?: return@mapNotNull null
    val startsAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    ClassAlert(c, startsAt - leadMinutes * 60_000L, startsAt)
}.filter { it.alertAt > after }.minByOrNull { it.alertAt }

/** `202601`-style code for the term [today] falls in (August starts the autumn term). */
fun termIdFor(today: LocalDate): String = when {
    today.monthValue >= 8 -> "${today.year}01"
    today.monthValue == 1 -> "${today.year - 1}01"
    else -> "${today.year - 1}02"
}

private val WEEKDAY_CN = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun weekdayCn(date: LocalDate): String = WEEKDAY_CN[date.dayOfWeek.value.coerceIn(1, 7)]

fun weekdayCn(n: Int): String = WEEKDAY_CN.getOrElse(n) { "周$n" }

// ---------------------------------------------------------------- model-facing text

object TimetableText {
    /** One class, e.g. `09:50-11:20  第3-4节  Java程序设计 / 葛敬军 / C301/303教室 · JDBC1`. */
    fun line(c: Course, season: Season): String {
        val span = Schedule.span(season, c)
        val head = if (span != null) "$span  第${c.slot}节" else "第${c.slot}节"
        val tail = buildString {
            append(c.course)
            if (c.teacher.isNotEmpty()) append(" / ").append(c.teacher)
            if (c.room.isNotEmpty()) append(" / ").append(c.room)
            if (c.note.isNotEmpty()) append(" · ").append(c.note)
        }
        return "$head  $tail"
    }

    fun day(courses: List<Course>, date: LocalDate, season: Season, term: Term): String {
        val week = term.weekOf(date)
        val head = "$date ${weekdayCn(date)}" + if (week > 0) "（第 $week 周）" else ""
        val list = courses.filter { it.date == date.toString() }.sortedBy { it.startPeriod }
        if (list.isEmpty()) return "$head\n  没课"
        return head + "\n" + list.joinToString("\n") { "  " + line(it, season) }
    }

    fun week(courses: List<Course>, week: Int, season: Season, term: Term): String {
        val monday = term.mondayOf(week) ?: return "第 $week 周：这个学期的第一周还不确定（先 timetable_sync）"
        val out = StringBuilder("第 $week 周  $monday → ${monday.plusDays(6)}")
        var any = false
        for (d in 0..6) {
            val day = monday.plusDays(d.toLong())
            val list = courses.filter { it.date == day.toString() }.sortedBy { it.startPeriod }
            if (list.isEmpty()) continue
            any = true
            out.append("\n").append(weekdayCn(day)).append(" ").append(day.toString().substring(5))
            list.forEach { out.append("\n  ").append(line(it, season)) }
        }
        if (!any) out.append("\n  这一周没课")
        return out.toString()
    }
}

// ---------------------------------------------------------------- .ics

object Ics {
    private fun escape(s: String) = s
        .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace("\r\n", "\\n").replace("\n", "\\n")

    private fun stamp(date: String, time: String) = date.replace("-", "") + "T" + time.replace(":", "") + "00"

    /** DTSTAMP is UTC by the spec. */
    private fun nowStamp(): String =
        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .format(java.time.Instant.now().atZone(java.time.ZoneOffset.UTC))

    /** RFC 5545 line folding: at most 75 octets, continuation lines start with a space, no split UTF-8 char. */
    fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val out = StringBuilder()
        val chunk = StringBuilder()
        var bytes = 0
        var limit = 75
        for (ch in line) {
            val n = ch.toString().toByteArray(Charsets.UTF_8).size
            if (bytes + n > limit) {
                if (out.isNotEmpty()) out.append("\r\n ")
                out.append(chunk)
                chunk.setLength(0)
                bytes = 0
                limit = 74
            }
            chunk.append(ch)
            bytes += n
        }
        if (out.isNotEmpty() && chunk.isNotEmpty()) out.append("\r\n ")
        out.append(chunk)
        return out.toString()
    }

    /** One VEVENT per class; classes without a 作息 entry are skipped ([skipped]). */
    fun build(courses: List<Course>, season: Season, calendarName: String): Pair<String, Int> {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//DSH//Timetable//CN",
            "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
            "X-WR-CALNAME:${escape(calendarName)}", "X-WR-TIMEZONE:Asia/Shanghai",
        )
        var skipped = 0
        courses.sortedWith(compareBy({ it.date }, { it.startPeriod })).forEach { c ->
            val start = Schedule.start(season, c.startPeriod)
            val end = Schedule.end(season, c.endPeriod)
            if (start == null || end == null || c.date.isEmpty()) { skipped++; return@forEach }
            val uid = "${c.date.replace("-", "")}-${c.startPeriod}-${stableId("${c.date}|${c.course}|${c.teacher}")}@dsh"
            lines += "BEGIN:VEVENT"
            lines += "UID:$uid"
            lines += "DTSTAMP:${nowStamp()}"
            lines += "DTSTART;TZID=Asia/Shanghai:${stamp(c.date, start)}"
            lines += "DTEND;TZID=Asia/Shanghai:${stamp(c.date, end)}"
            lines += "SUMMARY:${escape(c.course)}"
            if (c.room.isNotEmpty()) lines += "LOCATION:${escape(c.room)}"
            lines += "DESCRIPTION:${escape(listOf(c.teacher, c.type, c.note).filter { it.isNotEmpty() }.joinToString(" / "))}"
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } to skipped
    }

    /** Same input, same id on every run, so a re-import updates events instead of duplicating them. */
    private fun stableId(s: String): String {
        var h = 0
        s.forEach { h = h * 31 + it.code }
        return (h.toLong() and 0xffffffffL).toString(36)
    }
}
