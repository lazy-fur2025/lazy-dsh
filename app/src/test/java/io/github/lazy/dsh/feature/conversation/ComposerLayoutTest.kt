package io.github.lazy.dsh.feature.conversation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.data.Settings
import io.github.lazy.dsh.core.data.ThemeMode
import io.github.lazy.dsh.core.designsystem.DshTheme
import io.github.lazy.dsh.core.model.PermissionPreset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class ComposerLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compactHomeKeepsSendInsideAvailableHeight() = checkSend(height = 360, hero = true, multiline = false)

    @Test
    fun compactHomeWithLongDraftKeepsSendInsideAvailableHeight() = checkSend(height = 280, hero = true, multiline = true)

    @Test
    fun chatWithLongDraftKeepsSendInsideAvailableHeight() = checkSend(height = 240, hero = false, multiline = true)

    private fun checkSend(height: Int, hero: Boolean, multiline: Boolean) {
        var sendDescription = ""
        compose.setContent {
            DshTheme(ThemeMode.Dark) {
                sendDescription = stringResource(R.string.send_message)
                Box(Modifier.size(360.dp, height.dp).testTag("viewport")) {
                    if (hero) EmptyHeroLayout(Modifier.fillMaxSize()) { Draft(hero, multiline) }
                    else Draft(hero, multiline)
                }
            }
        }
        compose.onNodeWithContentDescription(sendDescription).assertIsDisplayed()
        val send = compose.onNodeWithContentDescription(sendDescription).fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Send must stay above the keyboard: $send outside $viewport",
            send.top >= viewport.top && send.bottom <= viewport.bottom &&
                send.left >= viewport.left && send.right <= viewport.right)
    }

    @Composable
    private fun Draft(hero: Boolean, multiline: Boolean) {
        val settings = Settings()
        Composer(
            state = androidx.compose.runtime.remember { ComposerState().apply {
                text = if (multiline) (1..30).joinToString("\n") { "Keyboard layout test $it" } else "Keyboard layout test"
            } },
            running = false,
            preset = PermissionPreset.Full,
            onPreset = {},
            model = settings.choice,
            providers = settings.providers,
            onModel = { _, _ -> },
            contextTokens = 0,
            onSend = {},
            onStop = {},
            hero = hero,
        )
    }
}
