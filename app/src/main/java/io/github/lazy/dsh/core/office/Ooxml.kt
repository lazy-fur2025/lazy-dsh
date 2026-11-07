package io.github.lazy.dsh.core.office

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The parts every OOXML file is: a ZIP of XML. docx, pptx and xlsx differ only in which parts they
 * carry, so the container and the escaping live here once.
 *
 * No dependency is used — `java.util.zip` writes the same archive a library would, and staying
 * inside the JDK keeps the export working in JVM tests and on every Android version.
 */
internal fun zipOf(entries: List<Pair<String, String>>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, body) in entries) {
            val entry = ZipEntry(name)
            // A fixed timestamp makes two exports of the same text byte-identical (tests compare).
            entry.time = 0L
            zip.putNextEntry(entry)
            zip.write(body.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

internal object Xml {
    private const val DECL = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

    /** The XML declaration every OOXML part starts with. */
    fun doc(body: String): String = "$DECL\n$body"

    /**
     * Escapes text for element content and attribute values. Control characters below U+0020 are
     * dropped: they are not representable in XML 1.0 and make Word refuse the whole file.
     */
    fun esc(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\n' -> sb.append("&#10;")
                ch == '\t' -> sb.append("&#9;")
                ch < ' ' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
