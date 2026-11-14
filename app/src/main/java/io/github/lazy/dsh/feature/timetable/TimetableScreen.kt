package io.github.lazy.dsh.feature.timetable

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.designsystem.BtnVariant
import io.github.lazy.dsh.core.designsystem.DButton
import io.github.lazy.dsh.core.designsystem.DDialog
import io.github.lazy.dsh.core.designsystem.DSheet
import io.github.lazy.dsh.core.designsystem.DSwitch
import io.github.lazy.dsh.core.designsystem.DTopBar
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.EmptyNote
import io.github.lazy.dsh.core.designsystem.FilterPill
import io.github.lazy.dsh.core.designsystem.IconBtn
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.designsystem.Segmented
import io.github.lazy.dsh.core.designsystem.Tag
import io.github.lazy.dsh.core.designsystem.Tone
import io.github.lazy.dsh.core.designsystem.pressable
import io.github.lazy.dsh.core.timetable.Course
import io.github.lazy.dsh.core.timetable.Schedule
import io.github.lazy.dsh.core.timetable.Season
import io.github.lazy.dsh.core.timetable.TimetableGrid
import io.github.lazy.dsh.core.timetable.TimetableReminders
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Width of one day column; seven of them scroll horizontally, the time rail stays put. */
private val DAY_WIDTH = 78.dp
private val TIME_RAIL = 38.dp
private val GRID_PAD = 0.dp

/**
 * 课表: the week the user is looking at (the current one by default), drawn as five 90-minute rows
 * and seven day columns, with a tap on a class opening its details. The data is the cached list the
 * agent tools use; 同步 re-reads it from the 教务系统 through the built-in browser's session.
 */
@Composable
fun TimetableScreen(
    onBack: () -> Unit,
    onAskAssistant: (String) -> Unit,
    vm: TimetableViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    var examTab by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Course?>(null) }
    var loginDialog by remember { mutableStateOf(false) }
    var notifDialog by remember { mutableStateOf(false) }
    var notificationsOk by remember { mutableStateOf(TimetableReminders.notificationsAllowed(context)) }

    // Coming back from the system notification settings (or the permission dialog) re-reads the gate.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOk = TimetableReminders.notificationsAllowed(context)
        vm.reload()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOk = granted && TimetableReminders.notificationsAllowed(context)
        if (granted) vm.setRemind(true) else notifDialog = true
    }

    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is TimetableViewModel.Event.Synced -> toast(context, context.getString(R.string.tt_sync_done, event.classes))
                is TimetableViewModel.Event.Failed -> toast(context, failureText(context, event.reason, event.detail))
                TimetableViewModel.Event.NeedLogin -> loginDialog = true
                TimetableViewModel.Event.NotificationsOff -> notifDialog = true
                is TimetableViewModel.Event.Exported -> {
                    if (!shareIcs(context, event.file)) toast(context, context.getString(R.string.tt_export_failed, ""))
                }
                is TimetableViewModel.Event.ExportFailed ->
                    toast(context, context.getString(R.string.tt_export_failed, event.detail.orEmpty()))
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        DTopBar(
            title = stringResource(R.string.timetable),
            onBack = onBack,
            glass = true,
            actions = {
                if (state.syncing) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Dsh.colors.labelSecondary)
                    }
                } else {
                    IconBtn(
                        R.drawable.dsw_refresh_outline, { if (examTab) vm.syncExams() else vm.syncNow() },
                        contentDescription = stringResource(R.string.tt_sync),
                    )
                }
                IconBtn(
                    R.drawable.dsw_sliders_two_outline, { settings = true },
                    contentDescription = stringResource(R.string.tt_settings),
                )
            },
        )
        Segmented(
            listOf(stringResource(R.string.timetable), stringResource(R.string.tt_exams)),
            if (examTab) 1 else 0,
            { examTab = it == 1 },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Unit
                examTab -> ExamList(
                    exams = state.exams,
                    term = state.examTerm,
                    fetchedAt = state.examsFetchedAt,
                    issue = state.examIssue,
                    syncing = state.syncing,
                    onSync = vm::syncExams,
                )
                !state.hasData -> NoTimetable(
                    syncing = state.syncing,
                    onSync = vm::syncNow,
                    onAskAssistant = {
                        onAskAssistant(context.getString(R.string.tt_login_prefill))
                    },
                )
                else -> TimetableBody(state, vm::previousWeek, vm::nextWeek, vm::thisWeek) { detail = it }
            }
        }
    }

    if (settings) {
        TimetableSettingsSheet(
            state = state,
            notificationsOk = notificationsOk,
            onDismiss = { settings = false },
            onSeason = vm::setSeason,
            onRemind = { on ->
                if (on && !notificationsOk) askNotifications() else vm.setRemind(on)
            },
            onLead = vm::setLead,
            onSync = vm::syncNow,
            onExport = vm::exportIcs,
            onAllowNotifications = ::askNotifications,
        )
    }

    detail?.let { course ->
        CourseSheet(course, state.season, onDismiss = { detail = null })
    }

    if (loginDialog) {
        DDialog(
            onDismiss = { loginDialog = false },
            title = stringResource(R.string.tt_login_title),
            buttons = {
                Spacer(Modifier.weight(1f))
                DButton(stringResource(R.string.cancel), { loginDialog = false }, variant = BtnVariant.Ghost)
                DButton(stringResource(R.string.tt_ask_login), {
                    loginDialog = false
                    onAskAssistant(context.getString(R.string.tt_login_prefill))
                })
            },
        ) {
            Text(
                stringResource(R.string.tt_login_body),
                style = DshType.s14, color = Dsh.colors.labelSecondary,
            )
        }
    }

    if (notifDialog) {
        DDialog(
            onDismiss = { notifDialog = false },
            title = stringResource(R.string.tt_need_notifications),
            buttons = {
                Spacer(Modifier.weight(1f))
                DButton(stringResource(R.string.cancel), { notifDialog = false }, variant = BtnVariant.Ghost)
                DButton(stringResource(R.string.tt_open_settings), {
                    notifDialog = false
                    openNotificationSettings(context)
                })
            },
        ) {
            Text(
                stringResource(R.string.tt_need_notifications_body),
                style = DshType.s14, color = Dsh.colors.labelSecondary,
            )
        }
    }
}

@Composable
private fun TimetableBody(
    state: TimetableViewModel.State,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onCourse: (Course) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WeekHeader(state, onPrevious, onNext, onThisWeek)
        WeekGrid(state, onCourse, Modifier.weight(1f))
    }
}

@Composable
private fun WeekHeader(
    state: TimetableViewModel.State,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    val c = Dsh.colors
    val monday = state.monday
    val sunday = monday?.plusDays(6)
    val range = if (monday != null && sunday != null) {
        "${monday.monthValue}/${monday.dayOfMonth} – ${sunday.monthValue}/${sunday.dayOfMonth}"
    } else ""
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tt_week_n, state.week), style = DshType.l20, color = c.labelPrimary)
                Spacer(Modifier.width(8.dp))
                if (state.thisWeek) {
                    Tag(stringResource(R.string.tt_this_week), Tone.Info)
                } else {
                    FilterPill(stringResource(R.string.tt_this_week), false, onThisWeek)
                }
            }
            Text(
                listOf(range, termLabel(state.termId)).filter { it.isNotEmpty() }.joinToString(" · "),
                style = DshType.xxs12, color = c.labelTertiary,
            )
            if (state.weekEmpty) {
                Text(stringResource(R.string.tt_empty_week), style = DshType.xxs12, color = c.labelCaption)
            }
        }
        IconBtn(
            R.drawable.dsw_chevron_left_outline, onPrevious,
            enabled = state.week > 1, contentDescription = stringResource(R.string.tt_prev_week),
        )
        IconBtn(
            R.drawable.dsw_chevron_right_outline, onNext,
            enabled = state.week < state.maxWeek, contentDescription = stringResource(R.string.tt_next_week),
        )
    }
}

@Composable
private fun WeekGrid(
    state: TimetableViewModel.State,
    onCourse: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Dsh.colors
    val density = LocalDensity.current
    val hScroll = rememberScrollState()
    val monday = state.monday ?: return
    val today = LocalDate.now()
    val days = TimetableGrid.week(state.courses, monday)
    val todayIndex = days.indexOfFirst { it.date == today }

    // Start on today (the week opens on the current day, not always Monday).
    LaunchedEffect(state.week, hScroll.maxValue) {
        if (hScroll.maxValue <= 0) return@LaunchedEffect
        val cell = with(density) { DAY_WIDTH.toPx() }.toInt()
        val target = if (todayIndex >= 0) (todayIndex - 1).coerceAtLeast(0) * cell else 0
        val last = (hScroll.maxValue / cell) * cell
        hScroll.scrollTo(((target / cell) * cell).coerceIn(0, last))
    }
    // A drag lands on a whole column, so a day header never sits half over the time rail.
    LaunchedEffect(hScroll) {
        snapshotFlow { hScroll.isScrollInProgress }.collect { dragging ->
            if (dragging || hScroll.maxValue <= 0) return@collect
            val cell = with(density) { DAY_WIDTH.toPx() }.toInt()
            val nearest = ((hScroll.value + cell / 2) / cell) * cell
            val last = (hScroll.maxValue / cell) * cell
            val target = nearest.coerceIn(0, last)
            if (target != hScroll.value) hScroll.animateScrollTo(target)
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(TIME_RAIL))
            Row(
                Modifier.horizontalScroll(hScroll).padding(horizontal = GRID_PAD),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                days.forEach { day ->
                    DayHeader(day.date, day.weekday, day.date == today, Modifier.width(DAY_WIDTH))
                }
                // One spare column: lets the last days scroll fully into view instead of being cut.
                Spacer(Modifier.width(DAY_WIDTH))
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.width(TIME_RAIL).fillMaxHeight()) {
                TimetableGrid.blocks.forEach { block ->
                    BlockLabel(block, state.season, Modifier.weight(1f))
                }
            }
            Row(
                Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll).padding(horizontal = GRID_PAD),
            ) {
                days.forEach { day ->
                    DayColumn(
                        day = day,
                        today = day.date == today,
                        season = state.season,
                        onCourse = onCourse,
                        modifier = Modifier.width(DAY_WIDTH).fillMaxHeight(),
                    )
                }
                Spacer(Modifier.width(DAY_WIDTH))
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, weekday: Int, today: Boolean, modifier: Modifier = Modifier) {
    val c = Dsh.colors
    Column(
        modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(weekdayRes(weekday)),
            style = DshType.xxxs11m,
            color = if (today) c.brandPrimary else c.labelTertiary,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (today) c.brandPrimary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = DshType.xxs12m,
                color = if (today) c.labelOnPrimary else c.labelPrimary,
            )
        }
    }
}

@Composable
private fun BlockLabel(periods: List<Int>, season: Season, modifier: Modifier = Modifier) {
    val c = Dsh.colors
    val start = Schedule.start(season, periods.first())
    Column(
        modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(periods.joinToString("-"), style = DshType.tiny10, color = c.labelTertiary, maxLines = 1)
        if (start != null) {
            Text(start, style = DshType.tiny10, color = c.labelCaption, maxLines = 1)
        }
    }
}

@Composable
private fun DayColumn(
    day: TimetableGrid.Day,
    today: Boolean,
    season: Season,
    onCourse: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Dsh.colors
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (today) c.brandPrimary.copy(alpha = if (c.isDark) 0.10f else 0.045f) else Color.Transparent)
            .drawBehind {
                drawLine(
                    c.borderL1.copy(alpha = 0.7f),
                    Offset(size.width - 0.5f, 0f),
                    Offset(size.width - 0.5f, size.height),
                    1f,
                )
            },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        day.byBlock.forEach { classes ->
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            c.borderL1,
                            Offset(0f, size.height - 0.5f),
                            Offset(size.width, size.height - 0.5f),
                            1f,
                        )
                    }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            ) {
                when {
                    classes.isEmpty() -> Unit
                    classes.size == 1 -> CourseCard(classes[0], season, onCourse, compact = false)
                    else -> classes.forEach { course ->
                        CourseCard(course, season, onCourse, compact = true, Modifier.weight(1f).fillMaxWidth())
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    season: Season,
    onCourse: (Course) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Dsh.colors
    val accent = courseAccent(course.course)
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.fillMaxSize().padding(vertical = 1.dp).clip(shape)
            .background(accent.copy(alpha = if (c.isDark) 0.26f else 0.13f))
            .pressable({ onCourse(course) }, shape),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp)) {
            Text(
                course.course,
                style = DshType.xxxs11m,
                color = c.labelPrimary,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                course.room.ifBlank { course.teacher },
                style = DshType.tiny10,
                color = c.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NoTimetable(
    syncing: Boolean,
    onSync: () -> Unit,
    onAskAssistant: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyNote(stringResource(R.string.tt_no_data), art = R.drawable.brand_branch)
        Text(
            stringResource(R.string.tt_no_data_hint),
            style = DshType.xs13, color = Dsh.colors.labelTertiary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 18.dp),
        )
        DButton(
            if (syncing) stringResource(R.string.tt_syncing) else stringResource(R.string.tt_sync_now),
            onSync,
            enabled = !syncing,
            icon = R.drawable.dsw_refresh_outline,
        )
        Spacer(Modifier.height(8.dp))
        DButton(
            stringResource(R.string.tt_ask_login),
            onAskAssistant,
            variant = BtnVariant.Ghost,
            size = io.github.lazy.dsh.core.designsystem.BtnSize.Sm,
        )
    }
}

@Composable
private fun TimetableSettingsSheet(
    state: TimetableViewModel.State,
    notificationsOk: Boolean,
    onDismiss: () -> Unit,
    onSeason: (Season) -> Unit,
    onRemind: (Boolean) -> Unit,
    onLead: (Int) -> Unit,
    onSync: () -> Unit,
    onExport: () -> Unit,
    onAllowNotifications: () -> Unit,
) {
    val c = Dsh.colors
    DSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tt_settings), Modifier.weight(1f), style = DshType.l20, color = c.labelPrimary)
                IconBtn(
                    R.drawable.dsw_close_outline, onDismiss,
                    contentDescription = stringResource(R.string.close),
                )
            }
            val syncedAt = remember(state.fetchedAt) { fmtFetched(state.fetchedAt) }
            Text(
                listOf(termLabel(state.termId), syncedAt?.let { stringResource(R.string.tt_synced_at, it) })
                    .filterNotNull().filter { it.isNotEmpty() }.joinToString(" · "),
                style = DshType.xxs12, color = c.labelTertiary,
            )

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.tt_season), style = DshType.xs13m, color = c.labelTertiary)
            Spacer(Modifier.height(8.dp))
            Segmented(
                listOf(stringResource(R.string.tt_season_winter), stringResource(R.string.tt_season_summer)),
                if (state.season == Season.Winter) 0 else 1,
                { if (it == 0) onSeason(Season.Winter) else onSeason(Season.Summer) },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tt_season_desc), style = DshType.xxs12, color = c.labelTertiary)

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tt_remind), style = DshType.s14, color = c.labelPrimary)
                    Text(stringResource(R.string.tt_remind_desc), style = DshType.xxs12, color = c.labelTertiary)
                }
                DSwitch(state.remind, onRemind)
            }
            if (state.remind) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.tt_lead), style = DshType.xxs12, color = c.labelTertiary)
                Spacer(Modifier.height(6.dp))
                val leads = listOf(0, 5, 10, 15, 30)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    leads.forEach { minutes ->
                        FilterPill(
                            if (minutes == 0) stringResource(R.string.tt_lead_zero) else stringResource(R.string.tt_lead_min, minutes),
                            minutes == state.lead,
                            { onLead(minutes) },
                        )
                    }
                }
            }
            if (!notificationsOk) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.tt_notifications_off),
                        Modifier.weight(1f), style = DshType.xxs12, color = c.warnLabel,
                    )
                    DButton(
                        stringResource(R.string.tt_open_settings), onAllowNotifications,
                        variant = BtnVariant.Outline,
                        size = io.github.lazy.dsh.core.designsystem.BtnSize.Sm,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            DButton(
                if (state.syncing) stringResource(R.string.tt_syncing) else stringResource(R.string.tt_sync_now),
                onSync,
                Modifier.fillMaxWidth(),
                enabled = !state.syncing,
                icon = R.drawable.dsw_refresh_outline,
            )
            Spacer(Modifier.height(8.dp))
            DButton(
                stringResource(R.string.tt_export),
                onExport,
                Modifier.fillMaxWidth(),
                variant = BtnVariant.Outline,
                icon = R.drawable.dsw_download_outline,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tt_export_hint),
                style = DshType.xxs12, color = c.labelTertiary,
            )
        }
    }
}

@Composable
private fun CourseSheet(course: Course, season: Season, onDismiss: () -> Unit) {
    val c = Dsh.colors
    val date = remember(course.date) { runCatching { LocalDate.parse(course.date) }.getOrNull() }
    val span = Schedule.span(season, course)
    DSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(course.course, style = DshType.l20, color = c.labelPrimary)
                    val tags = buildList {
                        if (course.type.isNotBlank()) add(course.type)
                        add(stringResource(R.string.tt_periods, course.slot))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { Tag(it, Tone.Neutral) }
                    }
                }
                IconBtn(
                    R.drawable.dsw_close_outline, onDismiss,
                    contentDescription = stringResource(R.string.close),
                )
            }
            Spacer(Modifier.height(16.dp))
            if (date != null) {
                SheetRow(stringResource(R.string.tt_time), buildString {
                    append(stringResource(R.string.tt_weekday_n, weekdayLabel(date.dayOfWeek.value)))
                    append("  ")
                    append("${date.monthValue}/${date.dayOfMonth}")
                    if (span != null) append("  ").append(span)
                })
            }
            if (course.room.isNotBlank()) SheetRow(stringResource(R.string.tt_room), course.room)
            if (course.teacher.isNotBlank()) SheetRow(stringResource(R.string.tt_teacher), course.teacher)
            if (course.klass.isNotBlank()) SheetRow(stringResource(R.string.tt_class), course.klass)
            if (course.note.isNotBlank()) SheetRow(stringResource(R.string.tt_note), course.note)
        }
    }
}

@Composable
private fun SheetRow(label: String, value: String) {
    val c = Dsh.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(vertical = 3.dp)) {
        Text(label, Modifier.width(72.dp), style = DshType.xs13, color = c.labelTertiary)
        Text(value, Modifier.weight(1f), style = DshType.xs13, color = c.labelPrimary)
    }
}

// ---------------------------------------------------------------- helpers

/** A stable accent per course name, so the same class keeps its colour across weeks. */
@Composable
private fun courseAccent(name: String): Color {
    val c = Dsh.colors
    val palette = listOf(c.info, c.success, c.business, c.warn, c.amber)
    val index = (name.hashCode() and 0x7fffffff) % palette.size
    return palette[index]
}

private fun weekdayRes(weekday: Int): Int = when (weekday) {
    1 -> R.string.tt_mon
    2 -> R.string.tt_tue
    3 -> R.string.tt_wed
    4 -> R.string.tt_thu
    5 -> R.string.tt_fri
    6 -> R.string.tt_sat
    else -> R.string.tt_sun
}

/** `202601` → `2026–2027 · 秋季学期`; a hand-imported code that is not six digits is shown as is. */
@Composable
private fun termLabel(id: String): String {
    if (id.length != 6) return id
    val year = id.take(4).toIntOrNull() ?: return id
    val season = stringResource(if (id.endsWith("01")) R.string.tt_term_autumn else R.string.tt_term_spring)
    return stringResource(R.string.tt_term, year, year + 1, season)
}

@Composable
private fun weekdayLabel(weekday: Int): String = stringResource(weekdayRes(weekday))

private fun fmtFetched(raw: String): String? =
    runCatching {
        DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(raw))
    }.getOrNull()

private fun failureText(context: Context, reason: TimetableViewModel.Reason, detail: String?): String = when (reason) {
    TimetableViewModel.Reason.Network -> context.getString(R.string.tt_sync_network, detail.orEmpty())
    TimetableViewModel.Reason.NotTimetable -> context.getString(R.string.tt_sync_bad, detail?.take(120).orEmpty())
    TimetableViewModel.Reason.Empty -> context.getString(R.string.tt_sync_empty)
    TimetableViewModel.Reason.NoDates -> context.getString(R.string.tt_sync_no_dates)
    TimetableViewModel.Reason.Unknown -> context.getString(R.string.tt_sync_failed, detail.orEmpty())
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

/** Hands the generated .ics to the calendar apps through the share sheet (no storage permission). */
private fun shareIcs(context: Context, file: File): Boolean = runCatching {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/calendar"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.tt_export)))
}.isSuccess

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}
