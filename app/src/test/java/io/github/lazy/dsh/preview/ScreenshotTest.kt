package io.github.lazy.dsh.preview

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.data.PerformanceMode
import io.github.lazy.dsh.core.data.Settings
import io.github.lazy.dsh.core.data.ThemeMode
import io.github.lazy.dsh.core.data.WorkDetails
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshTheme
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.GlassTier
import io.github.lazy.dsh.core.designsystem.IconBtn
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.designsystem.AuroraBackdrop
import io.github.lazy.dsh.core.designsystem.Whale
import io.github.lazy.dsh.core.designsystem.frost
import io.github.lazy.dsh.core.model.PermissionPreset
import io.github.lazy.dsh.feature.conversation.ChatStream
import io.github.lazy.dsh.feature.conversation.Composer
import io.github.lazy.dsh.feature.conversation.ComposerState
import io.github.lazy.dsh.feature.conversation.SessionDrawer
import io.github.lazy.dsh.feature.settings.OnboardingContent
import io.github.lazy.dsh.feature.settings.SettingsIndex
import io.github.lazy.dsh.feature.settings.SettingsPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Headless screenshots of the real composables (no device, no emulator).
 *
 *   gradlew :app:testDebugUnitTest --tests "io.github.lazy.dsh.preview.ScreenshotTest" \
 *     -Pdsh.fetchShizuku=false -I F:\DSH\toolchains\dev.init.gradle
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeChatDark() = shot("home-chat-dark", dark = true, empty = false)

    @Test
    fun homeChatLight() = shot("home-chat-light", dark = false, empty = false)

    @Test
    fun homeEmptyDark() = shot("home-empty-dark", dark = true, empty = true)

    @Test
    fun homeEmptyLight() = shot("home-empty-light", dark = false, empty = true)

    /** 360 dp: the OnePlus-class phone the toolbar has to survive on (the chip row is the tight one). */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun homeEmptyNarrow() = shot("home-empty-narrow", dark = false, empty = true)

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun homeChatNarrow() = shot("home-chat-narrow", dark = true, empty = false)

    /** Foldable/tablet width: the one-row toolbar still fits model, context, mic and send. */
    @Test
    @Config(qualifiers = "w600dp-h900dp-xxhdpi")
    fun homeChatWide() = shot("home-chat-wide", dark = true, empty = false)

    /**
     * A 3-column table from a real turn (file names in the last column): every column must show on
     * a 360 dp phone. Regression: the third column was clipped away entirely.
     */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun markdownTableNarrow() = markdownShot("markdown-table-narrow", TABLE_LONG)

    /** Four columns: still every column, scrollable. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun markdownTableWide() = markdownShot("markdown-table-wide", "| a | b | c | d |\n|---|---|---|---|\n| 1 | 2 | 3 | 4 |\n")

    private fun markdownShot(name: String, text: String) {
        compose.setContent {
            DshTheme(ThemeMode.Dark) {
                Box(Modifier.fillMaxSize().background(Dsh.colors.bgBase).padding(16.dp)) {
                    io.github.lazy.dsh.core.designsystem.DshMarkdown(text)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private companion object {
        /** A real turn's table: a wide last column (CJK + inline code + a date). */
        const val TABLE_LONG = "| # | Size | File |\n|---|------|------|\n" +
            "| 1 | 359.6 MB | `高中•回忆.docx` — 2025-07-21 |\n" +
            "| 2 | 143.5 MB | `wwcd_v1.zip` — 2026-07-04 |\n" +
            "| 3 | 58.6 MB | `Youre Walking.mp4` — 2026-10-27 |\n"
    }

    /** 能耗模式: the two identities, both light (the brand is a light-theme design). */
    @Test
    fun homeEmptyElectric() = shot("home-empty-electric", dark = false, empty = true, brand = PerformanceMode.Electric)

    @Test
    fun homeEmptyLeaf() = shot("home-empty-leaf", dark = false, empty = true, brand = PerformanceMode.Leaf)

    @Test
    fun performanceCardsLight() = performanceCards("performance-cards-light", dark = false, brand = PerformanceMode.Leaf)

    @Test
    fun performanceCardsDark() = performanceCards("performance-cards-dark", dark = true, brand = PerformanceMode.Leaf)

    /** The sheet's small marks in their real homes: empty states and the drawer's hairline. */
    @Test
    fun emptyStates() = emptyStatesShot("empty-states")

    /** 冷启动片头's calm variant (the film itself is a raw MP4 and needs a device to play). */
    @Test
    fun launchLight() = launchShot("launch-quiet-light", dark = false)

    @Test
    fun launchDark() = launchShot("launch-quiet-dark", dark = true)

    @Test
    fun drawerDark() = drawer("drawer-dark", dark = true)

    @Test
    fun drawerLight() = drawer("drawer-light", dark = false)

    @Test
    fun settingsDark() = settingsShot("settings-dark", dark = true)

    @Test
    fun settingsLight() = settingsShot("settings-light", dark = false)

    @Test
    fun onboardingDark() = onboardingShot("onboarding-dark", dark = true, withKey = false)

    @Test
    fun onboardingKeyDark() = onboardingShot("onboarding-key-dark", dark = true, withKey = true)

    @Test
    fun onboardingLight() = onboardingShot("onboarding-light", dark = false, withKey = false)

    private fun shot(name: String, dark: Boolean, empty: Boolean, brand: PerformanceMode = PerformanceMode.Electric) {
        compose.setContent { HomePreview(dark, empty, brand) }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun drawer(name: String, dark: Boolean) {
        compose.setContent {
            DshTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackdrop()
                    Box(Modifier.fillMaxHeight().width(300.dp)) {
                        SessionDrawer(
                            PreviewFixtures.sessions, PreviewFixtures.status, "s1",
                            onNew = {}, onOpen = {}, onAutomation = {}, onFiles = {}, onSettings = {}, onClose = {},
                            onRename = { _, _ -> }, onPin = { _, _ -> }, onMute = { _, _ -> },
                            onArchive = { _, _ -> }, onDelete = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun settingsShot(name: String, dark: Boolean) {
        compose.setContent {
            DshTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackdrop()
                    SettingsPage(stringResource(R.string.settings), {}) { SettingsIndex(0) {} }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun onboardingShot(name: String, dark: Boolean, withKey: Boolean) {
        compose.setContent {
            DshTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackdrop()
                    OnboardingContent(
                        key = if (withKey) "preview-placeholder" else "",
                        keyStatus = null,
                        onKeyChange = {}, onContinue = {}, onThirdParty = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun launchShot(name: String, dark: Boolean) {
        compose.setContent {
            DshTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                io.github.lazy.dsh.feature.launch.QuietIntro(animate = false)
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun emptyStatesShot(name: String) {
        compose.setContent {
            DshTheme(ThemeMode.Light) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackdrop()
                    Column(Modifier.fillMaxSize().padding(0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        io.github.lazy.dsh.core.designsystem.EmptyNote("No sessions yet", art = R.drawable.brand_paw)
                        io.github.lazy.dsh.core.designsystem.EmptyNote("Nothing saved yet", art = R.drawable.brand_branch)
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PerformanceMode.entries.forEach { m ->
                                androidx.compose.foundation.Image(
                                    androidx.compose.ui.res.painterResource(
                                        when (m) {
                                            PerformanceMode.Electric -> R.drawable.brand_seal_electric
                                            PerformanceMode.Leaf -> R.drawable.brand_seal_leaf
                                        },
                                    ),
                                    null, Modifier.width(28.dp).height(28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun performanceCards(name: String, dark: Boolean, brand: PerformanceMode) {        compose.setContent {
            DshTheme(if (dark) ThemeMode.Dark else ThemeMode.Light, brand = brand) {
                Box(Modifier.fillMaxSize()) {
                    AuroraBackdrop()
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        io.github.lazy.dsh.feature.conversation.PerformanceCard(PerformanceMode.Electric, false, onSelect = {})
                        io.github.lazy.dsh.feature.conversation.PerformanceCard(PerformanceMode.Leaf, true, onSelect = {})
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            io.github.lazy.dsh.feature.conversation.EnergySwitch(PerformanceMode.Leaf, {})
                            Spacer(Modifier.width(12.dp))
                            io.github.lazy.dsh.feature.conversation.EnergySwitch(PerformanceMode.Electric, {})
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Composable
    private fun HomePreview(dark: Boolean, empty: Boolean, brand: PerformanceMode = PerformanceMode.Electric) {
        DshTheme(if (dark) ThemeMode.Dark else ThemeMode.Light, brand = brand) {
            val c = Dsh.colors
            val settings: Settings = PreviewFixtures.settings
            Box(Modifier.fillMaxSize()) {
                AuroraBackdrop()
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 6.dp).height(48.dp)
                            .frost(Radius.lg, GlassTier.Thin, elevation = 8.dp).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBtn(R.drawable.dsw_panel_left_outline, {}, size = 40.dp, iconSize = 18.dp, tint = c.labelPrimary)
                        Text(
                            if (empty) "DSH" else "排查启动崩溃",
                            Modifier.weight(1f).padding(horizontal = 4.dp),
                            style = DshType.s14m, color = c.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (!empty) {
                            IconBtn(R.drawable.dsw_inspect_outline, {}, size = 40.dp, iconSize = 18.dp, tint = c.labelSecondary)
                            IconBtn(R.drawable.dsw_new_chat_outline, {}, size = 40.dp, iconSize = 18.dp, tint = c.labelPrimary)
                        }
                    }
                    if (empty) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 32.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            io.github.lazy.dsh.feature.conversation.HeroHeading(animate = false)
                            Spacer(Modifier.height(28.dp))
                            Box(Modifier.imePadding().navigationBarsPadding()) { ComposerPreview(settings, hero = true, brand = brand) }
                        }
                    } else {
                        ChatStream(
                            PreviewFixtures.conversation, PreviewFixtures.live, WorkDetails.Standard, rememberLazyListState(),
                            onInspect = {}, onRetry = {}, onOpenSettings = {},
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 4.dp)
                                .imePadding().navigationBarsPadding(),
                        ) { ComposerPreview(settings, hero = false, brand = brand) }
                    }
                }
            }
        }
    }

    @Composable
    private fun ComposerPreview(settings: Settings, hero: Boolean, brand: PerformanceMode = PerformanceMode.Electric) {
        val state = remember { ComposerState().apply { text = if (hero) "" else "那依赖版本具体锁到多少？" } }
        Composer(
            state = state,
            running = false,
            preset = PermissionPreset.Full,
            onPreset = {},
            model = settings.choice,
            providers = settings.providers,
            onModel = { _, _ -> },
            contextTokens = PreviewFixtures.conversation.contextTokens,
            onSend = {},
            onStop = {},
            hero = hero,
            performance = brand,
            mode = io.github.lazy.dsh.core.model.SessionModes.Default,
            modes = io.github.lazy.dsh.core.model.SessionModes.builtIns,
        )
    }
}
