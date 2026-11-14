package io.github.lazy.dsh.feature.timetable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.lazy.dsh.core.timetable.Course
import io.github.lazy.dsh.core.timetable.Ics
import io.github.lazy.dsh.core.timetable.Season
import io.github.lazy.dsh.core.timetable.Term
import io.github.lazy.dsh.core.timetable.TimetableReminders
import io.github.lazy.dsh.core.timetable.TimetableStore
import io.github.lazy.dsh.core.timetable.TimetableSync
import io.github.lazy.dsh.core.timetable.termIdFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/**
 * The 课表 page: the cached timetable, the week being looked at, and the few actions the screen
 * offers (sync, 作息, reminders, .ics export). The cache is plain files in [TimetableStore], so every
 * mutation reloads it; there is no second copy to keep in sync.
 */
@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val store: TimetableStore,
    private val sync: TimetableSync,
    private val reminders: TimetableReminders,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    data class State(
        val courses: List<Course> = emptyList(),
        val term: Term = Term(),
        val season: Season = Season.Winter,
        val remind: Boolean = false,
        val lead: Int = 15,
        val termId: String = "",
        val fetchedAt: String = "",
        val week: Int = 1,
        val maxWeek: Int = 1,
        val syncing: Boolean = false,
        val loading: Boolean = true,
        val exams: List<io.github.lazy.dsh.core.timetable.Exam> = emptyList(),
        val examTerm: String = "",
        val examsFetchedAt: String = "",
        val examIssue: String? = null,
    ) {
        val hasData: Boolean get() = courses.isNotEmpty()
        val today: LocalDate get() = LocalDate.now()
        val currentWeek: Int get() = term.weekOf(today)
        val thisWeek: Boolean get() = week == currentWeek
        val monday: LocalDate? get() = term.mondayOf(week)
        val weekEmpty: Boolean get() = monday != null && courses.none { it.date == monday.toString() }
    }

    enum class Reason { Network, NotTimetable, Empty, NoDates, Unknown }

    sealed interface Event {
        data class Synced(val classes: Int) : Event
        data class Failed(val reason: Reason, val detail: String? = null) : Event
        data object NeedLogin : Event
        data object NotificationsOff : Event
        data class Exported(val file: File, val skipped: Int) : Event
        data class ExportFailed(val detail: String?) : Event
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init { reload() }

    fun reload() {
        val courses = store.courses()
        val exams = store.exams()
        val term = store.term()
        val st = store.state()
        val current = term.weekOf(LocalDate.now())
        val lastWeek = courses.mapNotNull { c -> runCatching { LocalDate.parse(c.date) }.getOrNull() }
            .maxOfOrNull { term.weekOf(it) } ?: 0
        val max = maxOf(1, current, lastWeek, courses.maxOfOrNull { it.week } ?: 0)
        _state.update { prev ->
            val week = if (prev.loading) current.coerceIn(1, max) else prev.week.coerceIn(1, max)
            prev.copy(
                courses = courses,
                term = term,
                season = store.season(),
                remind = st.remind,
                lead = st.leadMinutes,
                termId = st.term,
                fetchedAt = st.fetchedAt,
                week = week,
                maxWeek = max,
                loading = false,
                exams = exams.items,
                examTerm = exams.term,
                examsFetchedAt = exams.fetchedAt,
            )
        }
    }

    fun previousWeek() = goWeek(-1)
    fun nextWeek() = goWeek(1)

    fun goWeek(delta: Int) = _state.update { it.copy(week = (it.week + delta).coerceIn(1, it.maxWeek)) }

    fun thisWeek() = _state.update {
        it.copy(week = it.term.weekOf(LocalDate.now()).coerceIn(1, it.maxWeek))
    }

    private suspend fun syncExamData(term: String) {
        when (val result = sync.syncExams(term)) {
            is TimetableSync.ExamFetch.Ok -> {
                reload()
                _state.update { it.copy(examIssue = null) }
            }
            TimetableSync.ExamFetch.NotLoggedIn -> {
                _state.update { it.copy(examIssue = context.getString(io.github.lazy.dsh.R.string.tt_exam_login)) }
                _events.emit(Event.NeedLogin)
            }
            is TimetableSync.ExamFetch.Failed ->
                _state.update { it.copy(examIssue = context.getString(io.github.lazy.dsh.R.string.tt_exam_failed)) }
        }
    }

    fun syncExams() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            try { syncExamData(termIdFor(LocalDate.now())) }
            finally { _state.update { it.copy(syncing = false) } }
        }
    }

    fun syncNow() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            try {
                val term = termIdFor(LocalDate.now())
                when (val fetched = sync.fetch(term)) {
                    is TimetableSync.Fetch.Ok -> when (val saved = sync.save(fetched.courses, term)) {
                        is TimetableSync.Save.Ok -> {
                            reload()
                            _state.update { it.copy(week = saved.todayWeek.coerceIn(1, it.maxWeek)) }
                            _events.emit(Event.Synced(saved.classes))
                        }
                        TimetableSync.Save.NoDates -> _events.emit(Event.Failed(Reason.NoDates))
                    }
                    is TimetableSync.Fetch.NotLoggedIn -> _events.emit(Event.NeedLogin)
                    is TimetableSync.Fetch.NotTimetable ->
                        _events.emit(Event.Failed(Reason.NotTimetable, fetched.snippet))
                    is TimetableSync.Fetch.Empty -> _events.emit(Event.Failed(Reason.Empty))
                    is TimetableSync.Fetch.Failed -> _events.emit(Event.Failed(Reason.Network, fetched.detail))
                }
                syncExamData(term)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(Event.Failed(Reason.Unknown, e.message ?: e.javaClass.simpleName))
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    fun setSeason(season: Season) {
        store.setSeason(season)
        reminders.resync()
        reload()
    }

    fun setRemind(on: Boolean) {
        if (on && !TimetableReminders.notificationsAllowed(context)) {
            _events.tryEmit(Event.NotificationsOff)
            return
        }
        store.setReminder(on, _state.value.lead)
        reminders.resync()
        reload()
    }

    fun setLead(minutes: Int) {
        store.setReminder(_state.value.remind, minutes)
        reminders.resync()
        reload()
    }

    /** Writes the cached timetable to a cache file and hands it to the calendar/share sheet. */
    fun exportIcs() {
        val st = _state.value
        if (st.courses.isEmpty()) {
            _events.tryEmit(Event.ExportFailed(null))
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                val (ics, skipped) = Ics.build(st.courses, st.season, "课表 ${st.termId}".trim())
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "timetable").apply { mkdirs() }
                    File(dir, "课表-${st.termId.ifBlank { "dsh" }}.ics").apply { writeText(ics) }
                }
                file to skipped
            }
            result.onSuccess { (file, skipped) -> _events.emit(Event.Exported(file, skipped)) }
                .onFailure { _events.emit(Event.ExportFailed(it.message ?: it.javaClass.simpleName)) }
        }
    }
}
