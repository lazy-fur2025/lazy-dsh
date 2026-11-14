package io.github.lazy.dsh.core.timetable

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The timetable as it lives on the phone: `dsh-home/data/timetable/` holds the parsed classes and a
 * small state file (term, week 1's Monday, the 作息 in force, when it was synced). Everything the
 * read tools answer comes from here, so they work with no session and no network.
 */
@Singleton
class TimetableStore @Inject constructor(@ApplicationContext private val context: Context) {
    @Serializable
    data class State(
        val term: String = "",
        val firstMonday: String = "",
        /** [Season.id] currently in force on campus; the school switches on its own schedule. */
        val season: String = Season.Winter.id,
        val fetchedAt: String = "",
        /** Class reminders: posted by a local alarm, no model involved. */
        val remind: Boolean = false,
        /** How many minutes before a class starts the reminder fires; 0 = at the start. */
        val leadMinutes: Int = 15,
    )

    private val dir = File(context.filesDir, "dsh-home/data/timetable")
    private val coursesFile = File(dir, "courses.json")
    private val stateFile = File(dir, "state.json")
    private val examsFile = File(dir, "exams.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun courses(): List<Course> = runCatching {
        if (!coursesFile.exists()) emptyList() else json.decodeFromString(COURSES, coursesFile.readText())
    }.getOrDefault(emptyList())

    fun state(): State = runCatching {
        if (!stateFile.exists()) State() else json.decodeFromString(State.serializer(), stateFile.readText())
    }.getOrDefault(State())

    fun season(): Season = Season.of(state().season) ?: Season.Winter

    @Serializable
    data class Exams(val term: String = "", val fetchedAt: String = "", val items: List<Exam> = emptyList())

    fun exams(): Exams = runCatching {
        examsFile.bufferedReader(Charsets.UTF_8).use { json.decodeFromString(Exams.serializer(), it.readText()) }
    }.getOrDefault(Exams())

    /** A successful empty list clears old exams; failed fetches never reach this method. */
    fun saveExams(items: List<Exam>, term: String): Exams {
        dir.mkdirs()
        val snapshot = Exams(term, java.time.Instant.now().toString(), items)
        val temporary = File(dir, "exams-${java.util.UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(json.encodeToString(Exams.serializer(), snapshot), Charsets.UTF_8)
            java.nio.file.Files.move(temporary.toPath(), examsFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
        return snapshot
    }

    fun term(): Term = Term(state().term, state().firstMonday)

    fun save(courses: List<Course>, term: String, firstMonday: String, fetchedAt: String) {
        dir.mkdirs()
        val st = state().copy(term = term, firstMonday = firstMonday, fetchedAt = fetchedAt)
        // Write the classes first: a state that points at a missing/old list would be worse than a stale term.
        coursesFile.writeText(json.encodeToString(COURSES, courses))
        stateFile.writeText(json.encodeToString(State.serializer(), st))
    }

    /** Reads a `courses.json` written by an earlier run (timetable_import). */
    fun decodeCourses(text: String): List<Course> = json.decodeFromString(COURSES, text)

    fun setSeason(season: Season): State {
        dir.mkdirs()
        val st = state().copy(season = season.id)
        stateFile.writeText(json.encodeToString(State.serializer(), st))
        return st
    }

    fun setReminder(enabled: Boolean, leadMinutes: Int): State {
        dir.mkdirs()
        val st = state().copy(remind = enabled, leadMinutes = leadMinutes.coerceIn(0, 120))
        stateFile.writeText(json.encodeToString(State.serializer(), st))
        return st
    }

    /** Path of the generated calendar, for `timetable_export`. */
    fun icsFile(): File = File(dir, "timetable.ics")

    private companion object {
        val COURSES = kotlinx.serialization.builtins.ListSerializer(Course.serializer())
    }
}
