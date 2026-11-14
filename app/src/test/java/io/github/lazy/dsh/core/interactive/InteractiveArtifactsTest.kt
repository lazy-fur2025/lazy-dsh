package io.github.lazy.dsh.core.interactive

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class InteractiveArtifactsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = java.io.File(context.filesDir, "interactive")

    @Before fun clean() { dir.deleteRecursively() }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test fun `saves, reads and updates one scene by id`() {
        val first = InteractiveArtifacts.save(context, "<h1>chip</h1>", "Snapdragon")
        assertTrue(InteractiveArtifacts.validId(first.id))
        assertEquals("Snapdragon", first.title)
        val loaded = InteractiveArtifacts.read(context, first.id)!!
        assertEquals("<h1>chip</h1>", loaded.html)
        assertEquals("Snapdragon", loaded.title)

        val updated = InteractiveArtifacts.save(context, "<h1>chip v2</h1>", null, first.id)
        assertEquals(first.id, updated.id)
        assertEquals("Snapdragon", updated.title) // the sidecar title survives a title-less update
        assertEquals("<h1>chip v2</h1>", InteractiveArtifacts.read(context, first.id)!!.html)
    }

    @Test fun `defaults the title and rejects unsafe ids and oversized pages`() {
        val artifact = InteractiveArtifacts.save(context, "<p>x</p>", "  ")
        assertEquals(context.getString(io.github.lazy.dsh.R.string.interactive_default_title), artifact.title)

        assertNull(InteractiveArtifacts.read(context, "../escape"))
        assertFalse(InteractiveArtifacts.validId("a/b"))
        assertTrue(runCatching { InteractiveArtifacts.save(context, "<p>x</p>", null, "../escape") }.isFailure)
        assertTrue(runCatching {
            InteractiveArtifacts.save(context, "x".repeat(InteractiveArtifacts.MAX_HTML_CHARS + 1), null)
        }.isFailure)
    }

    @Test fun `the intent names the sandboxed activity with the artifact id`() {
        val artifact = InteractiveArtifacts.save(context, "<p>x</p>", "T")
        val intent = InteractiveArtifacts.intent(context, artifact.id)
        assertEquals("io.github.lazy.dsh.feature.interactive.InteractiveActivity", intent.component?.className)
        assertEquals(artifact.id, intent.getStringExtra(InteractiveArtifacts.EXTRA_ID))
    }

    @Test fun `viewport units become pixels so a WebView's zero-height first layout cannot collapse them`() {
        assertEquals(
            "height:652px;max-height:482px",
            ViewportUnits.rewrite("height:100vh;max-height:74vh", 360, 652),
        )
        assertEquals(
            "width:360px;left:calc(360px - 20px)",
            ViewportUnits.rewrite("width:100vw;left:calc(100vw - 20px)", 360, 652),
        )
        assertEquals("unchanged", ViewportUnits.rewrite("unchanged", 0, 0))
    }
}
