package io.github.lazy.dsh.core.capability.tools

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.lazy.dsh.core.capability.CallContext
import io.github.lazy.dsh.core.capability.InvalidArgsException
import io.github.lazy.dsh.core.capability.Tool
import io.github.lazy.dsh.core.capability.ToolResult
import io.github.lazy.dsh.core.capability.ToolSet
import io.github.lazy.dsh.core.capability.bool
import io.github.lazy.dsh.core.capability.reqStr
import io.github.lazy.dsh.core.capability.str
import io.github.lazy.dsh.core.interactive.InteractiveArtifacts
import io.github.lazy.dsh.core.model.Risk
import io.github.lazy.dsh.core.model.ToolGroup
import io.github.lazy.dsh.core.platform.ActivityStarts
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns model-written HTML/CSS/JS into a real, interactive window on the phone. The app owns the
 * WebView and the sandbox; the model only supplies one self-contained page, so a question like
 * "what does this chip look like" can become a manipulable diagram instead of a paragraph.
 */
@Singleton
class InteractiveTools @Inject constructor(@ApplicationContext private val context: Context) : ToolSet {

    override val tools = listOf(
        object : Tool(
            "interactive_view", ToolGroup.Interactive, Risk.read, false,
            "Draw an interactive scene directly inside the conversation. Use it when a visual, manipulable answer is " +
                "clearer than text: a chip/board, a diagram, a timeline, a chart, a geometry or physics toy, a small " +
                "simulation. Write ONE self-contained HTML page with inline CSS and JavaScript (no external URLs, no " +
                "network, no files); size it for a phone and make the controls obvious. Pass the same id to update a " +
                "scene the user is iterating on. Returns an artifact id the chat card can reopen.",
            {
                string(
                    "html",
                    "The complete HTML document. Inline all CSS/JS, no external URLs; the window has no network or file access.",
                    required = true,
                )
                string("title", "Short window title, e.g. 'Snapdragon X Elite'.")
                string("id", "Reuse an existing artifact id to update it; omit to create a new window.")
                bool("open", "Optional full-screen window, default false. The scene always appears inline in the conversation.")
            },
        ) {
            override suspend fun invoke(args: JsonObject, call: CallContext): ToolResult {
                val html = args.reqStr("html")
                if (html.length > InteractiveArtifacts.MAX_HTML_CHARS) throw InvalidArgsException(
                    "html is too large (${html.length} characters; limit ${InteractiveArtifacts.MAX_HTML_CHARS})",
                )
                val id = args.str("id")?.trim()?.takeIf { it.isNotEmpty() }
                if (id != null && !InteractiveArtifacts.validId(id)) throw InvalidArgsException(
                    "id must be 1-64 letters, digits, _ or -",
                )
                val artifact = try {
                    InteractiveArtifacts.save(context, html, args.str("title"), id)
                } catch (e: IllegalArgumentException) {
                    throw InvalidArgsException(e.message ?: "invalid interactive artifact")
                }
                val opened = if (args.bool("open") == true) {
                    ActivityStarts.tryStart(context, InteractiveArtifacts.intent(context, artifact.id))
                } else false
                val payload = buildJsonObject {
                    put("id", artifact.id)
                    put("title", artifact.title)
                    put("bytes", artifact.html.length)
                    put("opened", opened)
                }
                return ToolResult.text(payload.toString())
            }
        },
    )
}
