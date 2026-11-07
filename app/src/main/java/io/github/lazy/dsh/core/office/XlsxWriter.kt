package io.github.lazy.dsh.core.office

/** One worksheet: the first row is the header (bold, frozen). */
data class Sheet(val name: String, val rows: List<List<String>>)

/**
 * Writes an Excel workbook (.xlsx).
 *
 * A model reply is prose with the occasional table, so the mapping is: every table becomes a sheet
 * named after the heading above it; without tables, the document is laid out as a two-column list
 * (kind, text). Inline strings are used instead of a shared-strings part — fewer parts, same result.
 */
object XlsxWriter {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun build(blocks: List<Block>, title: String?): ByteArray {
        val sheets = sheetsOf(blocks, title)
        val entries = mutableListOf<Pair<String, String>>()
        entries += "[Content_Types].xml" to Xml.doc(contentTypes(sheets.size))
        entries += "_rels/.rels" to Xml.doc(
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="$R/officeDocument" Target="xl/workbook.xml"/>""" +
                "</Relationships>",
        )
        entries += "xl/workbook.xml" to Xml.doc(workbook(sheets))
        val rels = StringBuilder(
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""",
        )
        sheets.indices.forEach { i ->
            rels.append("""<Relationship Id="rId${i + 1}" Type="$R/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
        }
        rels.append("""<Relationship Id="rId${sheets.size + 1}" Type="$R/styles" Target="styles.xml"/>""")
        rels.append("</Relationships>")
        entries += "xl/_rels/workbook.xml.rels" to Xml.doc(rels.toString())
        entries += "xl/styles.xml" to Xml.doc(styles)
        sheets.forEachIndexed { i, sheet ->
            entries += "xl/worksheets/sheet${i + 1}.xml" to Xml.doc(sheetXml(sheet))
        }
        return zipOf(entries)
    }

    /** Tables become sheets; a workbook with none still gets one, so the file is never empty. */
    fun sheetsOf(blocks: List<Block>, title: String?): List<Sheet> {
        val out = mutableListOf<Sheet>()
        var heading = title?.takeIf { it.isNotBlank() } ?: "Sheet1"
        for (block in blocks) {
            when (block) {
                is Block.Heading -> heading = MarkdownBlocks.plain(block.spans).ifBlank { heading }
                is Block.Table -> {
                    val rows = mutableListOf<List<String>>()
                    if (block.header.isNotEmpty()) rows += block.header
                    rows += block.rows
                    if (rows.isNotEmpty()) out += Sheet(unique(safeName(heading), out.map { it.name }), rows)
                    heading = "Sheet${out.size + 1}"
                }
                else -> Unit
            }
        }
        if (out.isNotEmpty()) return out
        val rows = mutableListOf(listOf("类型", "内容"))
        for (block in blocks) {
            val cell = when (block) {
                is Block.Heading -> "标题${block.level}" to MarkdownBlocks.plain(block.spans)
                is Block.Paragraph -> "正文" to MarkdownBlocks.plain(block.spans)
                is Block.Bullet -> "列表" to MarkdownBlocks.plain(block.spans)
                is Block.Numbered -> "编号" to MarkdownBlocks.plain(block.spans)
                is Block.Code -> "代码" to block.text
                is Block.Quote -> "引用" to MarkdownBlocks.plain(block.spans)
                is Block.Rule -> null
                is Block.Table -> null
            } ?: continue
            if (cell.second.isNotBlank()) rows += listOf(cell.first, cell.second)
        }
        return listOf(Sheet(title?.let(::safeName)?.takeIf { it.isNotBlank() } ?: "内容", rows))
    }

    private fun safeName(name: String): String {
        val cleaned = name.map { if (it in "[]:*?/\\'" || it < ' ') ' ' else it }.joinToString("").trim()
        return cleaned.ifBlank { "Sheet" }.take(31)
    }

    private fun unique(base: String, taken: List<String>): String {
        if (base !in taken) return base
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = base.take(31 - suffix.length) + suffix
            if (candidate !in taken) return candidate
            n++
        }
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append("""<workbook xmlns="$NS" xmlns:r="$R"><sheets>""")
        sheets.forEachIndexed { i, sheet ->
            append("""<sheet name="${Xml.esc(sheet.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun sheetXml(sheet: Sheet): String {
        val width = sheet.rows.maxOfOrNull { it.size } ?: 1
        val height = sheet.rows.size.coerceAtLeast(1)
        val sb = StringBuilder()
        sb.append("<worksheet xmlns=\"").append(NS).append("\" xmlns:r=\"").append(R).append("\">")
        sb.append("<dimension ref=\"A1:").append(column(width - 1)).append(height).append("\"/>")
        // The header row stays visible while scrolling.
        if (sheet.rows.isNotEmpty()) {
            sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
            sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            sb.append("</sheetView></sheetViews>")
        } else {
            sb.append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>")
        }
        sb.append("<cols>")
        for (c in 0 until width) {
            val longest = sheet.rows.maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 0
            val w = (longest + 2).coerceIn(10, 60)
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                .append("\" width=\"").append(w).append("\" customWidth=\"1\"/>")
        }
        sb.append("</cols><sheetData>")
        sheet.rows.forEachIndexed { r, cells ->
            sb.append("<row r=\"").append(r + 1).append("\">")
            cells.forEachIndexed { c, value ->
                if (value.isEmpty()) return@forEachIndexed
                val ref = column(c) + (r + 1)
                val style = if (r == 0) " s=\"1\"" else ""
                sb.append("<c r=\"").append(ref).append("\"").append(style)
                if (r > 0 && isNumber(value)) {
                    sb.append("><v>").append(value.trim().replace(",", "")).append("</v></c>")
                } else {
                    sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(Xml.esc(value)).append("</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** A plain number, never an identifier: `007`, `1,234` and `12月` stay text. */
    private fun isNumber(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 15) return false
        if (!Regex("-?(0|[1-9]\\d*)(\\.\\d+)?").matches(t)) return false
        return t.toDoubleOrNull() != null
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    private fun column(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private val styles = """<styleSheet xmlns="$NS">
<fonts count="2"><font><sz val="11"/><color theme="1"/><name val="Calibri"/></font><font><b/><sz val="11"/><color theme="1"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}
