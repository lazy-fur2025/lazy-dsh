package io.github.lazy.dsh

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.lazy.dsh.core.data.SecretStore
import io.github.lazy.dsh.core.data.SettingsRepository
import io.github.lazy.dsh.core.data.ThemeMode
import io.github.lazy.dsh.core.designsystem.DshTheme
import io.github.lazy.dsh.core.designsystem.LocalReducedMotion
import io.github.lazy.dsh.core.designsystem.Motion
import io.github.lazy.dsh.feature.conversation.HomeViewModel
import io.github.lazy.dsh.feature.settings.SettingsSection
import io.github.lazy.dsh.nav.HomeRoute
import io.github.lazy.dsh.nav.dshGraph
import io.github.lazy.dsh.nav.startOnboarding
import io.github.lazy.dsh.nav.toHome
import io.github.lazy.dsh.service.TapTargets
import io.github.lazy.dsh.nav.openSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(repo: SettingsRepository, secrets: SecretStore) : ViewModel() {
    private val deepSeek = secrets.read() != null
    /** Everything the root theme needs (appearance + glass switches), null while loading. */
    val config: StateFlow<io.github.lazy.dsh.core.data.Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** null = still loading (keeps the splash up). Second: a model credential exists — a DeepSeek
     *  API key or an enabled third-party provider. */
    val boot: StateFlow<Pair<ThemeMode, Boolean>?> = repo.settings
        .map { it.theme to (deepSeek || it.providers.any { p -> p.enabled }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/**
 * The app opens straight into the conversation (dsh home). Onboarding is only the API-key step
 * when there is none; everything else (Android permissions) is asked just in time from the
 * composer slot.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var extensions: io.github.lazy.dsh.core.extensions.ExtensionsRepository
    @Inject lateinit var sessions: io.github.lazy.dsh.core.session.SessionStore
    private var pendingSession by mutableStateOf<String?>(null)
    /** Settings section to open (EXTRA_SECTION, e.g. the quick panel's 添加 API Key / 打开自检). */
    private var pendingSection by mutableStateOf<String?>(null)
    private var navReady by mutableStateOf(false)

    /** Android 10–12: the in-app language (AppLanguage); 13+ does it itself. */
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(io.github.lazy.dsh.core.platform.AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only for a fresh launch: after recreation (rotation, theme) the user may have moved on.
        if (savedInstanceState == null) {
            pendingSession = sessionOf(intent)
            pendingSection = intent.getStringExtra(EXTRA_SECTION)
        }
        if (savedInstanceState == null) handleExtensionShare(intent)
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val boot by vm.boot.collectAsStateWithLifecycle()
            val config by vm.config.collectAsStateWithLifecycle()
            // Process-scoped: the intro plays on a cold start only, never on rotation or a theme change.
            var introDone by remember { mutableStateOf(IntroPlayed) }
            LaunchedEffect(Unit) { markIntroPlayed() }
            val theme = boot?.first
            // The system splash only covers the wait for the theme; the branded intro below takes
            // over from the first composed frame, so it can never be "squeezed out" by a page.
            splash.setKeepOnScreenCondition { theme == null }
            DshTheme(
                theme ?: ThemeMode.System,
                brand = config?.performance ?: io.github.lazy.dsh.core.data.PerformanceMode.Default,
                frost = config?.glassBlur ?: true,
                reduceTransparency = config?.reduceTransparency ?: false,
            ) {
                Box(Modifier.fillMaxSize()) {
                    // One aurora for the whole app; every page paints its chrome on top of it.
                    io.github.lazy.dsh.core.designsystem.AuroraBackdrop()
                    boot?.let { AppNav(it.second) }
                    // 冷启动片头: an overlay over the live app, so nothing waits for it and no
                    // page can cover it. Once per process — a rotation must not replay it.
                    if (!introDone) {
                        io.github.lazy.dsh.feature.launch.LaunchIntro(ready = navReady && boot != null) {
                            introDone = true
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSession = sessionOf(intent)
        pendingSection = intent.getStringExtra(EXTRA_SECTION)
        handleExtensionShare(intent)
    }

    /** The conversation to show: EXTRA_SESSION, or for EXTRA_OPEN_RUNNING the one running / waiting now (null: leave the page as it is). */
    private fun sessionOf(intent: Intent): String? =
        intent.getStringExtra(EXTRA_SESSION) ?: if (intent.getBooleanExtra(EXTRA_OPEN_RUNNING, false)) TapTargets.running(sessions.status.value) else null

    /**
     * 分享 → 安装为 DSH 扩展 (the `.ExtensionInstall` alias): copy a shared file into the app (the
     * share grant ends with this activity), then open 设置 → 扩展, which starts the install / import.
     */
    private fun handleExtensionShare(intent: Intent?) {
        if (intent?.component?.className?.endsWith(".ExtensionInstall") != true) return
        @Suppress("DEPRECATION")
        val stream = (intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)) ?: intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (stream == null && text.isNullOrEmpty()) return
        lifecycleScope.launch {
            val input = if (stream != null) runCatching { extensions.stage(stream).path }.getOrNull() else text
            if (input != null) { extensions.pendingInput.value = input; pendingSection = SettingsSection.Extensions.name }
        }
    }

    /**
     * The page tree (nav.DshNavGraph), home as the fixed start. Settings links from outside the tree
     * (EXTRA_SECTION, share) use nav.openSettings: home → 设置 → section.
     */
    @Composable
    private fun AppNav(hasCredentials: Boolean) {
        val nav = rememberNavController()
        val home: HomeViewModel = hiltViewModel()
        // Home is the fixed start; without a credential the first launch goes to onboarding (once, not after recreation).
        var routed by rememberSaveable { mutableStateOf(false) }
        // First, so the links below already see onboarding.
        LaunchedEffect(Unit) {
            if (!routed && !hasCredentials) nav.startOnboarding()
            routed = true; navReady = true
        }
        LaunchedEffect(pendingSession) {
            // Never a plugin's background session (#43; e.g. an old notification of one): home stays as it is.
            pendingSession?.let { id -> if (!sessions.isForeign(id)) home.open(id); nav.toHome(); pendingSession = null }
        }
        LaunchedEffect(pendingSection) {
            // Ignored during onboarding (home is not on the stack yet), as before.
            pendingSection?.let { sec -> pendingSection = null; nav.openSettings(sec) }
        }
        // Shared-axis push/pop (docs/design-tokens.md §5): a quarter-screen slide with a fade, so the
        // direction is felt without a full-screen sweep. Reduced motion snaps.
        val reduce = LocalReducedMotion.current
        NavHost(
            nav, startDestination = HomeRoute,
            enterTransition = {
                if (reduce) EnterTransition.None
                else slideInHorizontally(tween(Motion.glass, easing = Motion.enter)) { it / 5 } +
                    fadeIn(tween(220, easing = Motion.enter)) +
                    scaleIn(tween(Motion.glass, easing = Motion.enter), initialScale = 0.98f)
            },
            exitTransition = {
                if (reduce) ExitTransition.None
                else slideOutHorizontally(tween(Motion.glass, easing = Motion.enter)) { -it / 10 } +
                    fadeOut(tween(180, easing = Motion.standard)) +
                    scaleOut(tween(Motion.glass, easing = Motion.enter), targetScale = 0.985f)
            },
            popEnterTransition = {
                if (reduce) EnterTransition.None
                else slideInHorizontally(tween(Motion.glass, easing = Motion.enter)) { -it / 10 } +
                    fadeIn(tween(220, easing = Motion.enter)) +
                    scaleIn(tween(Motion.glass, easing = Motion.enter), initialScale = 0.985f)
            },
            popExitTransition = {
                if (reduce) ExitTransition.None
                else slideOutHorizontally(tween(Motion.glass, easing = Motion.enter)) { it / 5 } +
                    fadeOut(tween(180, easing = Motion.standard)) +
                    scaleOut(tween(Motion.glass, easing = Motion.enter), targetScale = 0.98f)
            },
        ) {
            dshGraph(nav) { home }
        }
    }

    companion object {
        /** 冷启动片头 has played in this process (it must not replay on rotation). */
        var IntroPlayed = false
            private set

        /** Called once the intro has been put on screen (or skipped). */
        fun markIntroPlayed() { IntroPlayed = true }

        const val EXTRA_SESSION = "session"        /** A SettingsSection name to open on arrival. */
        const val EXTRA_SECTION = "settings_section"
        /** The ongoing notifications' tap (Notifications.openRunning): the conversation running at tap time. */
        const val EXTRA_OPEN_RUNNING = "open_running"
    }
}
