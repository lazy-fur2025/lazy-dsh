package io.github.lazy.dsh.core.interactive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.lazy.dsh.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * One AI-drawn interactive scene: the HTML the model wrote, its title and when it last changed.
 * Kept under `files/interactive/` so the conversation's card still opens it after a restart.
 */
@Serializable
data class InteractiveArtifact(
    val id: String,
    val title: String,
    val html: String,
    val updatedAt: Long,
)

/** The small sidecar next to `<id>.html`; the HTML itself is the artifact, this is only its label. */
@Serializable
private data class Meta(val title: String = "", val updatedAt: Long = 0)

object InteractiveArtifacts {
    /** ~256 KB of HTML is a generous scene; more is usually a mistake or a runaway model. */
    const val MAX_HTML_CHARS = 256 * 1024

    const val EXTRA_ID = "interactive_id"
    private const val ACTIVITY = "io.github.lazy.dsh.feature.interactive.InteractiveActivity"

    /** 1-64 letters, digits, `_` or `-`: safe as a file name and stable across calls. */
    private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    private val json = Json { ignoreUnknownKeys = true }

    fun validId(id: String): Boolean = ID.matches(id)

    /**
     * Write (or update) one scene. A blank [requestedId] creates a new one; an existing id keeps the
     * card in the conversation working while the model iterates.
     */
    fun save(context: Context, html: String, title: String?, requestedId: String? = null): InteractiveArtifact {
        require(html.isNotBlank()) { "html is required" }
        require(html.length <= MAX_HTML_CHARS) { "html is too large (limit $MAX_HTML_CHARS characters)" }
        val id = requestedId?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        require(validId(id)) { "id must be 1-64 letters, digits, _ or -" }
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, "$id.html")
        val metaFile = File(dir, "$id.json")
        val previous = readMeta(metaFile)
        val now = System.currentTimeMillis()
        val cleanTitle = title?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
            ?: previous?.title?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.interactive_default_title)
        writeAtomically(file, html)
        writeAtomically(metaFile, json.encodeToString(Meta.serializer(), Meta(cleanTitle, now)))
        return InteractiveArtifact(id, cleanTitle, html, now)
    }

    /** The scene [id], or null when no such HTML exists (the card then asks the model to draw it again). */
    fun read(context: Context, id: String): InteractiveArtifact? {
        if (!validId(id)) return null
        val file = File(dir(context), "$id.html")
        if (!file.isFile) return null
        val html = runCatching { file.readText() }.getOrNull() ?: return null
        val meta = readMeta(File(dir(context), "$id.json"))
        return InteractiveArtifact(
            id = id,
            title = meta?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.interactive_default_title),
            html = html,
            updatedAt = meta?.updatedAt ?: file.lastModified(),
        )
    }

    fun intent(context: Context, id: String): Intent =
        Intent().setComponent(ComponentName(context.packageName, ACTIVITY)).putExtra(EXTRA_ID, id)

    private fun dir(context: Context): File = File(context.filesDir, "interactive")

    private fun readMeta(file: File): Meta? =
        runCatching { if (file.isFile) json.decodeFromString(Meta.serializer(), file.readText()) else null }.getOrNull()

    private fun writeAtomically(target: File, text: String) {
        val temp = File(target.parentFile, "${target.name}.${UUID.randomUUID()}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }
}
