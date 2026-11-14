package io.github.lazy.dsh.feature.conversation

import androidx.activity.compose.BackHandler
import androidx.annotation.ArrayRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import io.github.lazy.dsh.core.designsystem.LocalReducedMotion
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.data.Settings
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.GlassTier
import io.github.lazy.dsh.core.designsystem.IconBtn
import io.github.lazy.dsh.core.designsystem.Motion
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.designsystem.Segmented
import io.github.lazy.dsh.core.designsystem.Tag
import io.github.lazy.dsh.core.designsystem.Tone
import io.github.lazy.dsh.core.designsystem.Whale
import io.github.lazy.dsh.core.designsystem.frost
import io.github.lazy.dsh.core.designsystem.glassBlur
import io.github.lazy.dsh.core.greeting.Headline
import io.github.lazy.dsh.core.greeting.HeroMoment
import io.github.lazy.dsh.core.greeting.HolidayId
import io.github.lazy.dsh.core.greeting.Subline
import io.github.lazy.dsh.core.session.Conversation
import io.github.lazy.dsh.core.session.SessionLive
import io.github.lazy.dsh.feature.trajectory.TrajectoryView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single home screen: the conversation, with the dsh sidebar as a modal drawer and a
 * 对话 / 轨迹 toggle for the same session.
 */
@Composable
fun HomeScreen(vm: HomeViewModel, onSettings: (String?) -> Unit, onAutomation: () -> Unit, onFiles: () -> Unit, onTimetable: () -> Unit) {
    val c = Dsh.colors
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val status by vm.rowStatus.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    // Transient layers close before Back leaves home (BackHandlerAllowlistTest): the open drawer.
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    // Back in front with a conversation open (e.g. from the launcher after a background run): it is seen now.
    LifecycleResumeEffect(currentId) { vm.markSeen(); onPauseOrDispose {} }
    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = c.scrim,
        drawerContent = {
            val drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ModalDrawerSheet(Modifier.width(300.dp), drawerShape = drawerShape,
                drawerContainerColor = androidx.compose.ui.graphics.Color.Transparent, drawerTonalElevation = 0.dp,
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0)) {
                SessionDrawer(
                    sessions, status, currentId,
                    onNew = { vm.newSession(null); scope.launch { drawer.close() } },
                    onOpen = { vm.open(it); scope.launch { drawer.close() } },
                    onAutomation = { scope.launch { drawer.close() }; onAutomation() },
                    onFiles = { scope.launch { drawer.close() }; onFiles() },
                    onTimetable = { scope.launch { drawer.close() }; onTimetable() },
                    onSettings = { section -> scope.launch { drawer.close() }; onSettings(section) },
                    onClose = { scope.launch { drawer.close() } },
                    onRename = vm::rename, onPin = vm::pin, onMute = vm::mute, onArchive = vm::archive, onDelete = vm::delete,
                )
            }
        },
    ) {
        // The conversation steps back rather than being covered: a small scale + rounded corners while
        // the drawer is open. Snaps under reduced motion.
        val reducedMotion = LocalReducedMotion.current
        val depth by animateFloatAsState(
            if (!reducedMotion && drawer.isOpen) 1f else 0f,
            tween(if (reducedMotion) 0 else Motion.slow, easing = Motion.enter), label = "drawerDepth",
        )
        // Readability: the page under the drawer is sharp text seen through glass. Blur it while the
        // drawer is open, so the drawer's own labels win (no-op below API 31 / 模糊效果 off).
        val blurRadius by animateDpAsState(
            if (!reducedMotion && drawer.isOpen) 24.dp else 0.dp,
            tween(if (reducedMotion) 0 else Motion.slow, easing = Motion.enter), label = "drawerBlur",
        )
        val blurLayer = if (blurRadius > 0.dp) Modifier.glassBlur(blurRadius) else Modifier
        Box(
            Modifier.fillMaxSize().then(blurLayer)
                .graphicsLayer { scaleX = 1f - depth * 0.05f; scaleY = 1f - depth * 0.05f }
                .clip(RoundedCornerShape((depth * 22f).dp)),
        ) {
            ConversationPane(vm, onMenu = { scope.launch { drawer.open() } }, onSettings = onSettings, onAutomation = onAutomation)
        }
    }
}

@Composable
private fun ConversationPane(vm: HomeViewModel, onMenu: () -> Unit, onSettings: (String?) -> Unit, onAutomation: () -> Unit) {
    val c = Dsh.colors
    val live by vm.live.collectAsStateWithLifecycle()
    val conv by vm.conversation.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val prefill by vm.prefill.collectAsStateWithLifecycle()
    val composer = rememberComposerState(live.sessionId)
    LaunchedEffect(prefill, live.sessionId) {
        if (live.sessionId == vm.id) prefill?.let { composer.text = it; vm.prefill.value = null }
    }
    val empty = conv.isEmpty && !live.running
    // …and 轨迹, a view of the same session (not a page): Back returns to 对话.
    BackHandler(view == SessionView.Trajectory) { vm.view.value = SessionView.Chat }
    // While the run waits on the user (approval / question / permission / takeover card) nothing is
    // happening, so stop the "working" animations: the UI settles to idle, which also lets
    // accessibility services and UI automation read the card's buttons.
    val waitingOnUser = live.approval != null || live.question != null || live.permission != null || live.takeover != null
    val reduced = LocalReducedMotion.current || waitingOnUser
    CompositionLocalProvider(LocalReducedMotion provides reduced) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 6.dp).height(48.dp)
                .frost(Radius.lg, GlassTier.Thin, elevation = 8.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(R.drawable.dsw_panel_left_outline, onMenu, size = 40.dp, iconSize = 18.dp, tint = c.labelPrimary, contentDescription = stringResource(R.string.open_sidebar))
            Text(if (empty) "DSH" else live.title.ifBlank { conv.turns.firstOrNull()?.user?.text?.lineSequence()?.firstOrNull() ?: stringResource(R.string.new_session) },
                Modifier.weight(1f).padding(horizontal = 4.dp), style = DshType.s14m, color = c.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 对话 / 轨迹: one icon toggle instead of a segmented control, so the title keeps its room at 360 dp.
            if (!empty) {
                val traj = view == SessionView.Trajectory
                IconBtn(if (traj) R.drawable.dsw_flat_list_outline else R.drawable.dsw_inspect_outline,
                    { vm.view.value = if (traj) SessionView.Chat else SessionView.Trajectory }, size = 40.dp, iconSize = 18.dp,
                    tint = if (traj) c.labelPrimary else c.labelSecondary,
                    contentDescription = stringResource(if (traj) R.string.view_chat else R.string.view_trajectory))
            }
            // Nothing to leave on the empty home, so no 新会话 there.
            if (!empty) IconBtn(R.drawable.dsw_new_chat_outline, { vm.newSession(null) }, size = 40.dp, iconSize = 18.dp, tint = c.labelPrimary, contentDescription = stringResource(R.string.new_session))
        }
        val agentDisplay by vm.agentDisplay.collectAsStateWithLifecycle()
        agentDisplay?.takeIf { live.running }?.let { AgentPreview(vm, it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                Triple(view, empty, live.sessionId),
                transitionSpec = {
                    if (reduced) fadeIn(androidx.compose.animation.core.tween(0)) togetherWith fadeOut(androidx.compose.animation.core.tween(0))
                    else (fadeIn(androidx.compose.animation.core.tween(240, easing = Motion.enter)) +
                        scaleIn(androidx.compose.animation.core.tween(260, easing = Motion.enter), initialScale = 0.985f)) togetherWith
                        fadeOut(androidx.compose.animation.core.tween(160, easing = Motion.exit))
                },
                label = "pane",
            ) { (v, isEmpty, _) ->
                when {
                    v == SessionView.Trajectory -> TrajectoryView(vm)
                    isEmpty -> EmptyHero(vm, composer, settings, live, conv, onAutomation, onSettings)
                    // One list state per session (and per crossfading pane), so positions never leak across sessions.
                    else -> ChatStream(conv, live, settings.workDetails, rememberLazyListState(), onInspect = vm::inspect, onRetry = vm::retry, onContinue = { vm.continueTask() }, onOpenSettings = { onSettings(it) }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        if (!empty && view == SessionView.Chat) BottomDock(vm, composer, settings, live, conv, onAutomation, onSettings)
        else if (view == SessionView.Trajectory) {
            // Trajectory has no composer: keep the run stoppable from here.
            if (live.running) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), contentAlignment = Alignment.Center) {
                io.github.lazy.dsh.core.designsystem.DButton(stringResource(R.string.stop_generating), { vm.stop() },
                    variant = io.github.lazy.dsh.core.designsystem.BtnVariant.Outline, icon = R.drawable.dsw_stop_fill)
            } else Spacer(Modifier.navigationBarsPadding())
        }
    }
    }
}

/** dsh EmptyHero: centered whale + 探索未至之境 + 预览版 pill, composer below. No suggestion chips. */
@Composable
private fun EmptyHero(vm: HomeViewModel, composer: ComposerState, settings: Settings, live: SessionLive, conv: Conversation, onAutomation: () -> Unit, onSettings: (String?) -> Unit) {
    EmptyHeroLayout(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        ComposerSlot(vm, composer, settings, live, conv, hero = true, onAutomation = onAutomation, onManageModes = { onSettings("Modes") })
    }
}

/** Keep the complete composer visible when the IME leaves too little room for the welcome art. */
@Composable
internal fun EmptyHeroLayout(modifier: Modifier = Modifier, composer: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val roomy = maxHeight >= 520.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = if (roomy) 32.dp else 4.dp),
            verticalArrangement = if (roomy) Arrangement.Center else Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (roomy) {
                HeroHeading()
                Spacer(Modifier.height(28.dp))
            }
            composer()
        }
    }
}

/** Shared by the live home and screenshots so the visual review uses the actual component. */
@Composable
internal fun HeroHeading(animate: Boolean = true) {
    val c = Dsh.colors
    val mode = io.github.lazy.dsh.core.designsystem.LocalPerformanceMode.current
    val reduced = LocalReducedMotion.current
    // A single entrance, not a loop: the emblem rises into place and the text follows.
    var entered by remember { mutableStateOf(!animate) }
    val spec = tween<Float>(if (animate && !reduced) Motion.glass else 0, easing = Motion.enter)
    val rise by animateFloatAsState(if (entered) 0f else 20f, spec, label = "heroRise")
    val fade by animateFloatAsState(if (entered) 1f else 0f, spec, label = "heroFade")
    LaunchedEffect(Unit) { entered = true }
    val (greeting, care) = rememberHeroGreeting()
    Column(
        Modifier.graphicsLayer { alpha = fade; translationY = rise.dp.toPx() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The name is set in the mode's own face: heavy and tight in 电荷, light serif in 叶绿.
        io.github.lazy.dsh.core.designsystem.ModeWordmark(
            mode, size = 12.sp,
            suffix = stringResource(io.github.lazy.dsh.core.designsystem.modeLabel(mode)).uppercase(),
        )
        // The character of the mode in effect: the app's face, and the clearest sign of which mode runs.
        androidx.compose.animation.AnimatedContent(
            targetState = mode,
            transitionSpec = {
                if (reduced) {
                    androidx.compose.animation.fadeIn(tween(0)).togetherWith(androidx.compose.animation.fadeOut(tween(0)))
                } else {
                    (androidx.compose.animation.fadeIn(tween(260, easing = Motion.enter)) +
                        androidx.compose.animation.scaleIn(
                            androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 420f),
                            initialScale = 0.88f,
                        ))
                        .togetherWith(
                            androidx.compose.animation.fadeOut(tween(150, easing = Motion.exit)) +
                                androidx.compose.animation.scaleOut(tween(150, easing = Motion.exit), targetScale = 0.94f),
                        )
                }
            },
            label = "heroCharacter",
        ) { m ->
            val glowColor = PerformanceUi.emblemAccent(m, c.isDark)
            val quiet = io.github.lazy.dsh.core.designsystem.LocalReduceTransparency.current
            Box(Modifier.size(152.dp), contentAlignment = Alignment.Center) {
                if (!quiet) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                0f to glowColor.copy(alpha = if (c.isDark) 0.22f else 0.16f),
                                0.65f to glowColor.copy(alpha = if (c.isDark) 0.06f else 0.04f),
                                1f to androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    }
                }
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(PerformanceUi.avatar(m)), null,
                    Modifier.size(144.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
        Text(greeting, Modifier.padding(horizontal = 24.dp),
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp),
            color = c.labelPrimary, textAlign = TextAlign.Center)
        Text(care, Modifier.padding(horizontal = 32.dp),
            style = DshType.xs13, color = c.labelSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * The home page speaks to the user instead of labelling itself. The date chooses the headline
 * (festival > Friday evening > weekend > weekday) and the clock chooses the line under it
 * (early bird / night owl / ordinary care), so "周五晚上" and "夜猫子" can appear together.
 * Both are drawn once per moment; a one-minute tick keeps the page honest across midnight and
 * the 05:00 / 07:30 / 18:00 / 23:00 boundaries without re-rolling while nothing changes.
 */
@Composable
private fun rememberHeroGreeting(): Pair<String, String> {
    val now by produceState(initialValue = java.time.LocalDateTime.now()) {
        while (true) {
            val current = java.time.LocalDateTime.now()
            val nextMinute = current.plusMinutes(1).withSecond(0).withNano(0)
            delay(java.time.Duration.between(current, nextMinute).toMillis().coerceAtLeast(500L))
            value = java.time.LocalDateTime.now()
        }
    }
    val moment = remember(now) { HeroMoment.of(now) }
    val headlineRes = headlineArray(moment.headline)
    val sublineRes = sublineArray(moment.subline)
    val lines = stringArrayResource(headlineRes)
    val sublines = stringArrayResource(sublineRes)
    val i = rememberSaveable(headlineRes) { mutableIntStateOf(if (lines.isEmpty()) 0 else Random.nextInt(lines.size)) }
    val j = rememberSaveable(sublineRes) { mutableIntStateOf(if (sublines.isEmpty()) 0 else Random.nextInt(sublines.size)) }
    return (lines.getOrNull(i.intValue % maxOf(lines.size, 1)) ?: stringResource(R.string.hero_title)) to
        (sublines.getOrNull(j.intValue % maxOf(sublines.size, 1)) ?: stringResource(R.string.hero_subtitle))
}

@ArrayRes
private fun headlineArray(headline: Headline): Int = when (headline) {
    is Headline.Holiday -> when (headline.id) {
        HolidayId.NEW_YEAR_EVE -> R.array.hero_holiday_new_year_eve
        HolidayId.NEW_YEAR -> R.array.hero_holiday_new_year
        HolidayId.SPRING_FESTIVAL -> R.array.hero_holiday_spring_festival
        HolidayId.QINGMING -> R.array.hero_holiday_qingming
        HolidayId.LABOR_DAY -> R.array.hero_holiday_labor_day
        HolidayId.DRAGON_BOAT -> R.array.hero_holiday_dragon_boat
        HolidayId.MID_AUTUMN -> R.array.hero_holiday_mid_autumn
        HolidayId.NATIONAL_DAY -> R.array.hero_holiday_national_day
    }
    Headline.FridayEvening -> R.array.hero_fri_evening
    is Headline.Weekend -> when (headline.day) {
        java.time.DayOfWeek.SUNDAY -> R.array.hero_sun
        else -> R.array.hero_sat
    }
    is Headline.Weekday -> when (headline.day) {
        java.time.DayOfWeek.MONDAY -> R.array.hero_mon
        java.time.DayOfWeek.TUESDAY -> R.array.hero_tue
        java.time.DayOfWeek.WEDNESDAY -> R.array.hero_wed
        java.time.DayOfWeek.THURSDAY -> R.array.hero_thu
        java.time.DayOfWeek.FRIDAY -> R.array.hero_fri
        java.time.DayOfWeek.SATURDAY -> R.array.hero_sat
        java.time.DayOfWeek.SUNDAY -> R.array.hero_sun
    }
}

@ArrayRes
private fun sublineArray(subline: Subline): Int = when (subline) {
    Subline.EARLY_BIRD -> R.array.hero_earlybird
    Subline.NIGHT_OWL -> R.array.hero_nightowl
    Subline.CARE -> R.array.hero_care
}

/** Bottom dock: [todo dock | queue dock] above the composer slot (approval ▸ permission ▸ question ▸ composer). */
@Composable
private fun BottomDock(vm: HomeViewModel, composer: ComposerState, settings: Settings, live: SessionLive, conv: Conversation, onAutomation: () -> Unit, onSettings: (String?) -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        conv.todos?.let { if (live.running) TodoDock(it) }
        QueueDock(live.queue, onEdit = { q -> vm.removeQueued(q.id); composer.text = q.text }, onDelete = { vm.removeQueued(it.id) }, onSendNow = { vm.sendQueuedNow(it.id) })
        Box(Modifier.padding(horizontal = 8.dp)) { ComposerSlot(vm, composer, settings, live, conv, hero = false, onAutomation = onAutomation, onManageModes = { onSettings("Modes") }) }
    }
}

/** First non-null wins: permission card, approval card, question card, composer. */
@Composable
fun ComposerSlot(vm: ConversationController, composer: ComposerState, settings: Settings, live: SessionLive, conv: Conversation, hero: Boolean,
                 onAutomation: (() -> Unit)? = null, showChips: Boolean = true, canSendEmpty: Boolean = false,
                 prepare: (String) -> String = { it }, onManageModes: (() -> Unit)? = null,
                 /** Bumped by a voice summon (assistant gesture, ball long-press): start listening. */
                 listenRequest: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelChangePending by vm.modelChangePending.collectAsStateWithLifecycle()
    val send = {
        val text = prepare(composer.outgoingText(context))
        val images = composer.images.map { it.mime to it.base64 }
        composer.clear()
        vm.send(text, images, now = settings.busySend == io.github.lazy.dsh.core.data.BusySend.Steer)
    }
    val voice = io.github.lazy.dsh.feature.voice.rememberVoiceInput(composer) {
        // Attachments still loading: leave the spoken text in the draft for a manual send.
        if (composer.pending == 0 && !composer.isEmpty) send()
    }
    val cardShown = live.takeover != null || live.permission != null || live.approval != null || live.question != null
    // A summon can land just before the panel switches to its recent session (a new composer, so a new
    // VoiceInput): a request taken in the last moment is replayed on the new one.
    var handled by remember { mutableIntStateOf(0) }
    var handledAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listenRequest, voice) {
        val now = android.os.SystemClock.uptimeMillis()
        val replay = listenRequest == handled && listenRequest > 0 && now - handledAt < 1500
        if ((listenRequest > handled || replay) && !cardShown) { if (!replay) handledAt = now; handled = listenRequest; voice.start() }
    }
    // Queued messages the run never sent (stopped / failed) come back into the composer.
    LaunchedEffect(live.sessionId, live.restoredDraft) {
        val draft = live.restoredDraft ?: return@LaunchedEffect
        composer.text = listOf(draft, composer.text).filter { it.isNotBlank() }.joinToString("\n\n")
        vm.consumeRestoredDraft()
    }
    when {
        live.takeover != null -> TakeoverCard(live.takeover) { vm.answerTakeover(live.takeover.requestKey, it) }
        live.permission != null -> PermissionCard(live.permission) { vm.answerPermission(live.permission.requestKey, it) }
        live.approval != null -> ApprovalCard(live.approval) { vm.approve(live.approval, it) }
        live.question != null -> QuestionCard(live.question, onSubmit = { vm.answer(live.question, it) }, onSkip = { vm.skipQuestion(live.question) })
        else -> Composer(
            composer, live.running, live.preset, vm::setPreset, settings.choice, settings.providers, vm::setModel, conv.contextTokens,
            onSend = { voice.cancel(); send() },
            onStop = { vm.stop() },
            reasoning = settings.reasoning, reasoningChoice = settings.reasoningChoice, onReasoning = vm::setReasoning,
            performance = settings.performance, onPerformance = vm::setPerformance,
            hero = hero,
            actions = ComposerActions(
                onNewSession = if (conv.isEmpty) null else ({ vm.newSession() }),
                onTrajectory = if (conv.isEmpty) null else ({ vm.view.value = SessionView.Trajectory }),
                onAutomation = onAutomation,
            ),
            showChips = showChips,
            canSendEmpty = canSendEmpty,
            modelChangePending = modelChangePending,
            mode = settings.mode(live.modeId),
            modes = settings.allModes,
            modeLocked = !live.modeChangeable,
            onMode = vm::setMode,
            onManageModes = onManageModes,
            voice = voice,
        )
    }
}
