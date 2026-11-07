package io.github.lazy.dsh.core.office

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OfficeFormat(val extension: String, val mime: String) {
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

/**
 * 文件: turns a reply into a real Office file, keeps it where the app can list it, and hands it to
 * an editor.
 *
 * Files live in the app's own `files/exports` (no storage permission, survives restarts, appears in
 * 文件) and are shared through the app's FileProvider. Editing happens in whatever app the user has
 * — Word, WPS, Sheets — because a phone IDE for OOXML is not something this app should grow.
 */
object OfficeExport {
    /** `files/exports`, the only directory the FileProvider exposes as `exports/`. */
    const val DIR = "exports"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** The exported files, newest first. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.length() > 0 && f.extension.lowercase() in EXTENSIONS }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    private val EXTENSIONS = OfficeFormat.entries.map { it.extension }.toSet()

    fun build(markdown: String, format: OfficeFormat, title: String? = null): ByteArray {
        val blocks = MarkdownBlocks.parse(markdown)
        val name = title?.takeIf { it.isNotBlank() } ?: titleOf(markdown)
        return when (format) {
            OfficeFormat.DOCX -> DocxWriter.build(blocks, name)
            OfficeFormat.PPTX -> PptxWriter.build(blocks, name)
            OfficeFormat.XLSX -> XlsxWriter.build(blocks, name)
        }
    }

    /** Writes [markdown] as [format]; returns the file (its name is unique in [DIR]). */
    fun save(context: Context, markdown: String, format: OfficeFormat, title: String? = null, now: Long = System.currentTimeMillis()): File {
        val bytes = build(markdown, format, title)
        val file = uniqueFile(context, fileName(title ?: titleOf(markdown), format, now))
        file.writeBytes(bytes)
        return file
    }

    fun fileName(title: String?, format: OfficeFormat, now: Long): String {
        val base = title
            ?.map { if (it in "/\\:*?\"<>|" || it < ' ') ' ' else it }
            ?.joinToString("")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.trim('.')
            ?.take(40)
            ?.takeIf { it.isNotBlank() }
            ?: "DSH-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
        return "$base.${format.extension}"
    }

    /** The first heading, else the first non-blank line, stripped of Markdown noise. */
    fun titleOf(markdown: String): String? {
        val first = markdown.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it != "---" }
            ?: return null
        val text = MarkdownBlocks.plain(MarkdownBlocks.inline(first.trimStart('#').trim()))
            .replace(Regex("[`*_~#>]"), "")
            .trim()
        return text.takeIf { it.isNotBlank() }?.take(40)
    }

    private fun uniqueFile(context: Context, name: String): File {
        val dir = dir(context)
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.')
        var candidate = File(dir, name)
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n).$ext")
            n++
        }
        return candidate
    }

    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Opens [file] in an installed viewer/editor; false when the phone has no app for it. */
    fun open(context: Context, file: File): Boolean {
        val format = OfficeFormat.entries.firstOrNull { it.extension == file.extension.lowercase() } ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, file), format.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /** Sends [file] to another app (chat, mail, cloud drive). */
    fun share(context: Context, file: File): Boolean {
        val format = OfficeFormat.entries.firstOrNull { it.extension == file.extension.lowercase() } ?: return false
        val send = Intent(Intent.ACTION_SEND)
            .setType(format.mime)
            .putExtra(Intent.EXTRA_STREAM, uri(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val chooser = Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser); true }.getOrDefault(false)
    }

    /** "1.2 MB", "860 KB", "12 KB" — what the 文件 list shows under each name. */
    fun size(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
