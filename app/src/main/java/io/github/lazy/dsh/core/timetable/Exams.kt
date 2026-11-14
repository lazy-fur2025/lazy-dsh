package io.github.lazy.dsh.core.timetable

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.LocalTime

/** One row of the school's xsksap!getDataList.action; times are NOT class periods. */
@Serializable
data class Exam(
    val id: String = "",
    val course: String,
    val date: String = "",
    val time: String = "",
    val room: String = "",
    val campus: String = "",
    val seat: String = "",
    val kind: String = "",
    val arrangement: String = "",
    val form: String = "",
    val note: String = "",
) {
    val day: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
    val start: LocalTime? get() = clocks.firstOrNull()
    val end: LocalTime? get() = clocks.getOrNull(1)
    private val clocks: List<LocalTime> get() = CLOCK.findAll(time).mapNotNull {
        runCatching { LocalTime.parse(it.value.padStart(5, '0')) }.getOrNull()
    }.toList()
    val clockLabel: String get() = if (start != null && end != null) "$start–$end" else time

    companion object { private val CLOCK = Regex("(?<![0-9])[0-9]{1,2}:[0-9]{2}(?![0-9])") }
}

/** Strict envelope validation: a login/error page must never replace a valid cache with emptiness. */
object ExamParser {
    fun parse(body: String): List<Exam> {
        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw NotExamJson()
        val rows = root["rows"] as? JsonArray ?: throw NotExamJson()
        val exams = rows.map { raw ->
            val o = raw as? JsonObject ?: throw NotExamJson()
            val course = o.s("kcmc").takeIf { it.isNotBlank() } ?: throw NotExamJson()
            Exam(
                id = o.s("ksrcdm"), course = course, date = o.s("ksrq"), time = o.s("kssj"),
                room = o.s("kscdmc").replace('\\', '/'), campus = o.s("xqmc"), seat = o.s("zwh"),
                kind = o.s("kslbmc"), arrangement = o.s("ksaplxmc"), form = o.s("ksxs"), note = o.s("bz"),
            )
        }
        val total = (root["total"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        // Only a complete, successful list may clear the cache. Missing/invalid counts are not emptiness.
        if (total == null || total < 0 || total != exams.size) throw NotExamJson()
        return exams.sortedWith(compareBy<Exam> { it.day ?: LocalDate.MAX }.thenBy { it.start ?: LocalTime.MAX }.thenBy { it.course })
    }

    private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
}

class NotExamJson : Exception("The exam endpoint did not return a complete exam list")

fun examsText(exams: List<Exam>): String = exams.joinToString("\n\n") { exam ->
    buildString {
        append(exam.course).append("\n").append(exam.date.ifBlank { "Date pending" })
        append("  ").append(exam.clockLabel.ifBlank { "Time pending" })
        val details = listOf(exam.campus, exam.room, exam.seat.takeIf { it.isNotBlank() }?.let { "Seat $it" }, exam.kind, exam.arrangement, exam.note)
            .filterNotNull().filter { it.isNotBlank() }
        if (details.isNotEmpty()) append("\n").append(details.joinToString(" · "))
    }
}
