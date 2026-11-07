package io.github.lazy.dsh.core.office

/**
 * A run of inline text carrying the formatting the three writers can express.
 * Bold, italic and code map to every OOXML dialect; links keep their text only (a Word hyperlink
 * needs a relationship part per target, not worth it for an export).
 */
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
)

/** One block of a Markdown document, flattened into what a document writer can render. */
sealed interface Block {
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Paragraph(val spans: List<Span>) : Block
    data class Bullet(val spans: List<Span>, val depth: Int) : Block
    data class Numbered(val spans: List<Span>, val depth: Int) : Block
    data class Code(val text: String, val lang: String?) : Block
    data class Quote(val spans: List<Span>) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
    data object Rule : Block
}

/**
 * The subset of Markdown an exported document needs: headings, paragraphs, lists, fenced code,
 * quotes, pipe tables, rules, and inline bold / italic / code.
 *
 * It is deliberately a line scanner, not a full CommonMark parser: an export that renders 95% of
 * model output correctly and never throws beats a strict parser that refuses a stray bracket.
 */
object MarkdownBlocks {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val numbered = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val rule = Regex("^\\s{0,3}([-*_])(?:\\s*\\1){2,}\\s*$")
    private val fence = Regex("^\\s{0,3}```\\s*([\\w+#.-]*)\\s*$")

    fun parse(markdown: String): List<Block> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }

            val fenceOpen = fence.matchEntire(line)
            if (fenceOpen != null) {
                val lang = fenceOpen.groupValues[1].ifBlank { null }
                val body = StringBuilder()
                i++
                while (i < lines.size && fence.matchEntire(lines[i]) == null) {
                    body.append(lines[i]).append('\n')
                    i++
                }
                if (i < lines.size) i++ // closing fence
                out += Block.Code(body.toString().trimEnd('\n'), lang)
                continue
            }

            val head = heading.matchEntire(line)
            if (head != null) {
                out += Block.Heading(head.groupValues[1].length, inline(head.groupValues[2].trim()))
                i++
                continue
            }
            if (rule.matches(line)) { out += Block.Rule; i++; continue }

            // A pipe table: the header row, then a separator row of dashes.
            if (line.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                val header = cells(line)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows += cells(lines[i])
                    i++
                }
                out += Block.Table(header, rows)
                continue
            }

            if (line.trimStart().startsWith(">")) {
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    if (quote.isNotEmpty()) quote.append(' ')
                    quote.append(lines[i].trimStart().removePrefix(">").trim())
                    i++
                }
                out += Block.Quote(inline(quote.toString()))
                continue
            }

            val num = numbered.matchEntire(line)
            if (num != null) {
                out += Block.Numbered(inline(num.groupValues[3].trim()), depth(num.groupValues[1]))
                i++
                continue
            }
            val bul = bullet.matchEntire(line) // after `rule`, so `- - -` stays a rule
            if (bul != null) {
                out += Block.Bullet(inline(bul.groupValues[2].trim()), depth(bul.groupValues[1]))
                i++
                continue
            }

            // A paragraph runs until the next block-level line.
            val para = StringBuilder(line.trim())
            i++
            while (i < lines.size && lines[i].isNotBlank() && !startsBlock(lines[i])) {
                para.append(' ').append(lines[i].trim())
                i++
            }
            out += Block.Paragraph(inline(para.toString()))
        }
        return out
    }

    /** Depth by indentation: two spaces (or one tab) per level, capped so a writer never runs out. */
    private fun depth(indent: String): Int {
        val width = indent.fold(0) { acc, c -> acc + if (c == '\t') 4 else 1 }
        return (width / 2).coerceIn(0, 3)
    }

    private fun startsBlock(line: String): Boolean =
        heading.matches(line) || numbered.matches(line) || bullet.matches(line) || rule.matches(line) ||
            fence.matches(line) || line.trimStart().startsWith(">") || line.trimStart().startsWith("|") ||
            line.trimStart().startsWith("```")

    private fun isTableSeparator(line: String): Boolean {
        val t = line.trim()
        if (!t.contains('-') || !t.contains('|') && !t.startsWith('-')) return false
        return t.all { it == '|' || it == '-' || it == ':' || it == ' ' } && t.contains('-')
    }

    private fun cells(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.dropLast(1)
        return t.split('|').map { it.trim() }
    }

    /**
     * Inline pass: `**b**`, `*i*`, `_i_`, `` `code` ``, `~~strike~~` (kept as plain text), and
     * `[label](url)` / `<url>` (label only). `_` only emphasises between non-word characters, so
     * `snake_case_name` survives intact.
     */
    fun inline(text: String): List<Span> {
        val out = mutableListOf<Span>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) { out += Span(buf.toString()); buf.setLength(0) }
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val mark = text.substring(i, i + 2)
                    val end = text.indexOf(mark, i + 2)
                    if (end > i + 2) {
                        flush(); out += Span(text.substring(i + 2, end), bold = true); i = end + 2
                    } else { buf.append(c); i++ }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) { flush(); out += Span(text.substring(i + 2, end)); i = end + 2 } else { buf.append(c); i++ }
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) { flush(); out += Span(text.substring(i + 1, end), code = true); i = end + 1 } else { buf.append(c); i++ }
                }
                c == '*' || c == '_' -> {
                    val safe = c == '*' || (i > 0 && !text[i - 1].isLetterOrDigit())
                    val end = if (safe) matchingEmphasis(text, i, c) else -1
                    if (end > i + 1) {
                        flush(); out += Span(text.substring(i + 1, end), italic = true); i = end + 1
                    } else { buf.append(c); i++ }
                }
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    val open = if (close > 0 && close + 1 < text.length && text[close + 1] == '(') text.indexOf(')', close + 2) else -1
                    if (close > i + 1 && open > close) {
                        flush(); out += Span(text.substring(i + 1, close)); i = open + 1
                    } else { buf.append(c); i++ }
                }
                c == '<' -> {
                    val end = text.indexOf('>', i + 1)
                    val inside = if (end > i + 1) text.substring(i + 1, end) else ""
                    if (inside.startsWith("http://") || inside.startsWith("https://")) {
                        flush(); out += Span(inside); i = end + 1
                    } else { buf.append(c); i++ }
                }
                else -> { buf.append(c); i++ }
            }
        }
        flush()
        return if (out.isEmpty()) listOf(Span("")) else out
    }

    /** The closing emphasis mark: same char, not followed by a word character. */
    private fun matchingEmphasis(text: String, from: Int, mark: Char): Int {
        var j = from + 1
        while (j < text.length) {
            if (text[j] == mark) {
                val after = if (j + 1 < text.length) text[j + 1] else ' '
                if (!after.isLetterOrDigit()) return j
            }
            j++
        }
        return -1
    }

    /** Plain text of a span list, for writers that cannot format (slide bullets, sheet cells). */
    fun plain(spans: List<Span>): String = spans.joinToString("") { it.text }
}
