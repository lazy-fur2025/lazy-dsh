package io.github.lazy.dsh.core.timetable

import io.github.lazy.dsh.core.capability.tools.BrowserTools
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that pulls the timetable from the 教务系统 and stores it. The agent tools and the
 * 课表 screen both go through here, so a sync started from either lands in the same cache and arms
 * the same class reminders.
 *
 * The login itself is not re-implemented: [BrowserTools] carries the school's cookies, so a session
 * the user established through `timetable_login` (or the built-in browser) is what makes this work.
 */
@Singleton
class TimetableSync @Inject constructor(
    private val store: TimetableStore,
    private val browser: BrowserTools,
    private val reminders: TimetableReminders,
) {
    /** The list endpoint's answer, already classified for the caller's own wording. */
    sealed interface Fetch {
        data class Ok(val courses: List<Course>) : Fetch

        /** The endpoint answered the login page: the browser has no live session. */
        data class NotLoggedIn(val snippet: String) : Fetch

        /** Something else came back (an error page, a changed endpoint). */
        data class NotTimetable(val snippet: String) : Fetch

        data class Empty(val term: String) : Fetch

        data class Failed(val detail: String) : Fetch
    }

    /** The cache after [save]. */
    sealed interface Save {
        data class Ok(
            val term: String,
            val classes: Int,
            val courses: Int,
            val monday: LocalDate,
            val todayWeek: Int,
        ) : Save

        /** A timetable without `pkrq` dates: week 1 cannot be placed. */
        data object NoDates : Save
    }

    suspend fun fetch(term: String): Fetch {
        val body = try {
            browser.pageText(listUrl(term))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Fetch.Failed(e.message ?: e.javaClass.simpleName)
        }
        val courses = try {
            TimetableParser.parse(body)
        } catch (e: NotTimetableJson) {
            return if (LOGIN_MARKERS.any { body.contains(it) }) Fetch.NotLoggedIn(e.snippet)
            else Fetch.NotTimetable(e.snippet)
        }
        if (courses.isEmpty()) return Fetch.Empty(term)
        return Fetch.Ok(courses)
    }

    sealed interface ExamFetch {
        data class Ok(val exams: List<Exam>) : ExamFetch
        data object NotLoggedIn : ExamFetch
        data class Failed(val detail: String) : ExamFetch
    }

    /** Exams use their own endpoint and cache: a failure must not destroy previously saved data. */
    suspend fun syncExams(term: String): ExamFetch {
        require(term.matches(Regex("[0-9]{6}"))) { "term must look like 202601" }
        return try {
            val body = browser.pageText(examsUrl(term))
            val exams = try {
                ExamParser.parse(body)
            } catch (e: NotExamJson) {
                return if (LOGIN_MARKERS.any { body.contains(it) }) ExamFetch.NotLoggedIn
                else ExamFetch.Failed(e.message.orEmpty())
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.saveExams(exams, term) }
            ExamFetch.Ok(exams)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ExamFetch.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Replaces the cache, re-arms class reminders, and reports what the new list holds. */
    fun save(courses: List<Course>, term: String): Save {
        val monday = firstMondayOf(courses) ?: return Save.NoDates
        store.save(courses, term, monday.toString(), Instant.now().toString())
        reminders.resync()
        return Save.Ok(
            term = term,
            classes = courses.size,
            courses = courses.map { it.course }.distinct().size,
            monday = monday,
            todayWeek = store.term().weekOf(LocalDate.now()),
        )
    }

    companion object {
        const val BASE = "http://210.44.16.13"

        /**
         * What the login page says in plain text. A timetable JSON never contains these, so they are
         * safe to look for; the form's own field names (verifycode) are not in innerText.
         */
        val LOGIN_MARKERS = listOf("立即登录", "忘记密码", "验证码", "统一认证", "教务管理系统", "verifycode")

        fun listUrl(term: String) =
            "$BASE/new/xsgrkbcx!getDataList.action?xnxqdm=$term&zc=&page=1&rows=1000&sort=kxh&order=asc"

        fun examsUrl(term: String) =
            "$BASE/new/xsksap!getDataList.action?xnxqdm=$term&page=1&rows=1000"
    }
}
