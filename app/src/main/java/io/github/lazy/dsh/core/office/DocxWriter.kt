package io.github.lazy.dsh.core.office

/**
 * Writes a Word document (.docx) from [Block]s.
 *
 * Only the parts Word needs are emitted: content types, the package and document relationships, the
 * document body, styles, and numbering. No images, hyperlinks or themes — an export should open
 * cleanly everywhere (Word, WPS, LibreOffice) rather than use every feature.
 */
object DocxWriter {
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    /** Page body width in twips: A4 (11906) minus 1275 twips of margin on each side. */
    private const val BODY_TWIPS = 9356

    fun build(blocks: List<Block>, title: String?): ByteArray = zipOf(
        listOf(
            "[Content_Types].xml" to Xml.doc(contentTypes()),
            "_rels/.rels" to Xml.doc(
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="$R/officeDocument" Target="word/document.xml"/>""" +
                    "</Relationships>",
            ),
            "word/_rels/document.xml.rels" to Xml.doc(
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="$R/styles" Target="styles.xml"/>""" +
                    """<Relationship Id="rId2" Type="$R/numbering" Target="numbering.xml"/>""" +
                    "</Relationships>",
            ),
            "word/styles.xml" to Xml.doc(styles()),
            "word/numbering.xml" to Xml.doc(numbering()),
            "word/document.xml" to Xml.doc(document(blocks, title)),
        ),
    )

    private fun contentTypes(): String =
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""" +
            """<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>""" +
            "</Types>"

    private fun document(blocks: List<Block>, title: String?): String {
        val body = StringBuilder()
        if (!title.isNullOrBlank()) body.append(paragraph("Title", listOf(Span(title))))
        // The heading that named the document is not repeated as a second title.
        val rest = if (!title.isNullOrBlank()) {
            val first = blocks.firstOrNull()
            if (first is Block.Heading && MarkdownBlocks.plain(first.spans) == title) blocks.drop(1) else blocks
        } else {
            blocks
        }
        rest.forEach { body.append(render(it)) }
        // A body must end with a paragraph; Word repairs the file if a table is last.
        body.append("""<w:p><w:pPr><w:spacing w:after="0"/></w:pPr></w:p>""")
        return """<w:document xmlns:w="$W" xmlns:r="$R"><w:body>$body""" +
            """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>""" +
            """<w:pgMar w:top="1275" w:right="1275" w:bottom="1275" w:left="1275" w:header="851" w:footer="992" w:gutter="0"/></w:sectPr>""" +
            "</w:body></w:document>"
    }

    private fun render(block: Block): String = when (block) {
        is Block.Heading -> paragraph("Heading${block.level.coerceIn(1, 4)}", block.spans)
        is Block.Paragraph -> paragraph(null, block.spans)
        is Block.Bullet -> listParagraph(block.spans, block.depth, numId = 1)
        is Block.Numbered -> listParagraph(block.spans, block.depth, numId = 2)
        is Block.Quote -> paragraph("Quote", block.spans)
        is Block.Code -> code(block.text)
        is Block.Rule -> rule()
        is Block.Table -> table(block)
    }

    private fun paragraph(style: String?, spans: List<Span>): String {
        val pPr = if (style != null) """<w:pPr><w:pStyle w:val="$style"/></w:pPr>""" else ""
        return "<w:p>$pPr${runs(spans)}</w:p>"
    }

    private fun listParagraph(spans: List<Span>, depth: Int, numId: Int): String {
        val level = depth.coerceIn(0, 2)
        val indent = 420 + level * 420
        return """<w:p><w:pPr><w:pStyle w:val="ListParagraph"/>""" +
            """<w:numPr><w:ilvl w:val="$level"/><w:numId w:val="$numId"/></w:numPr>""" +
            """<w:ind w:left="$indent" w:hanging="420"/></w:pPr>""" + runs(spans) + "</w:p>"
    }

    private fun code(text: String): String {
        val lines = text.split('\n').ifEmpty { listOf("") }
        return lines.mapIndexed { index, line ->
            val before = if (index == 0) 80 else 0
            val after = if (index == lines.lastIndex) 80 else 0
            """<w:p><w:pPr><w:shd w:val="clear" w:color="auto" w:fill="F3F4F6"/>""" +
                """<w:spacing w:before="$before" w:after="$after" w:line="240" w:lineRule="auto"/></w:pPr>""" +
                """<w:r><w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:eastAsia="Consolas" w:cs="Consolas"/>""" +
                """<w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr>""" +
                """<w:t xml:space="preserve">${Xml.esc(line.ifEmpty { " " })}</w:t></w:r></w:p>"""
        }.joinToString("")
    }

    private fun rule(): String =
        """<w:p><w:pPr><w:pBdr><w:bottom w:val="single" w:sz="6" w:space="1" w:color="D0D5DD"/></w:pBdr>""" +
            """<w:spacing w:before="120" w:after="120"/></w:pPr></w:p>"""

    private fun table(t: Block.Table): String {
        val columns = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
        val width = BODY_TWIPS / columns
        val sb = StringBuilder()
        sb.append("""<w:tbl><w:tblPr><w:tblW w:w="$BODY_TWIPS" w:type="dxa"/>""")
        sb.append("""<w:tblBorders><w:top w:val="single" w:sz="4" w:color="D0D5DD"/>""")
        sb.append("""<w:left w:val="single" w:sz="4" w:color="D0D5DD"/>""")
        sb.append("""<w:bottom w:val="single" w:sz="4" w:color="D0D5DD"/>""")
        sb.append("""<w:right w:val="single" w:sz="4" w:color="D0D5DD"/>""")
        sb.append("""<w:insideH w:val="single" w:sz="4" w:color="D0D5DD"/>""")
        sb.append("""<w:insideV w:val="single" w:sz="4" w:color="D0D5DD"/></w:tblBorders>""")
        sb.append("""<w:tblCellMar><w:left w:w="108" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar>""")
        sb.append("</w:tblPr><w:tblGrid>")
        repeat(columns) { sb.append("""<w:gridCol w:w="$width"/>""") }
        sb.append("</w:tblGrid>")
        sb.append(row(t.header, columns, header = true))
        t.rows.forEach { sb.append(row(it, columns, header = false)) }
        sb.append("</w:tbl>")
        // Word needs a paragraph between a table and the next block.
        sb.append("""<w:p><w:pPr><w:spacing w:after="0"/></w:pPr></w:p>""")
        return sb.toString()
    }

    private fun row(cells: List<String>, columns: Int, header: Boolean): String {
        val sb = StringBuilder("<w:tr>")
        for (index in 0 until columns) {
            val text = cells.getOrNull(index).orEmpty()
            sb.append("""<w:tc><w:tcPr><w:tcW w:w="${BODY_TWIPS / columns}" w:type="dxa"/>""")
            if (header) sb.append("""<w:shd w:val="clear" w:color="auto" w:fill="F3F4F6"/>""")
            sb.append("</w:tcPr>")
            sb.append("""<w:p><w:pPr><w:spacing w:before="40" w:after="40"/></w:pPr>""")
            sb.append(runs(listOf(Span(text, bold = header))))
            sb.append("</w:p></w:tc>")
        }
        sb.append("</w:tr>")
        return sb.toString()
    }

    private fun runs(spans: List<Span>): String = spans.joinToString("") { span ->
        if (span.text.isEmpty()) {
            ""
        } else {
            val rPr = buildString {
                if (span.code) append("""<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:eastAsia="Consolas"/>""")
                if (span.bold) append("<w:b/><w:bCs/>")
                if (span.italic) append("<w:i/><w:iCs/>")
            }
            val props = if (rPr.isEmpty()) "" else "<w:rPr>$rPr</w:rPr>"
            """<w:r>$props<w:t xml:space="preserve">${Xml.esc(span.text)}</w:t></w:r>"""
        }
    }

    private fun styles(): String = """<w:styles xmlns:w="$W">
<w:docDefaults>
<w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Microsoft YaHei" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="300" w:lineRule="auto"/></w:pPr></w:pPrDefault>
</w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="0" w:after="240"/></w:pPr><w:rPr><w:b/><w:sz w:val="40"/><w:szCs w:val="40"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="120"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="300" w:after="100"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="80"/><w:outlineLvl w:val="2"/></w:pPr><w:rPr><w:b/><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading4"><w:name w:val="heading 4"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="200" w:after="60"/><w:outlineLvl w:val="3"/></w:pPr><w:rPr><w:b/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:pPr><w:pBdr><w:left w:val="single" w:sz="12" w:space="8" w:color="D0D5DD"/></w:pBdr><w:ind w:left="284"/></w:pPr><w:rPr><w:i/><w:color w:val="475467"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="60"/><w:contextualSpacing/></w:pPr></w:style>
</w:styles>"""

    private fun numbering(): String = """<w:numbering xmlns:w="$W">
<w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/>
<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#8226;"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="420" w:hanging="420"/></w:pPr><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:hint="default"/></w:rPr></w:lvl>
<w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#9702;"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="840" w:hanging="420"/></w:pPr><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:hint="default"/></w:rPr></w:lvl>
<w:lvl w:ilvl="2"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#9642;"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="1260" w:hanging="420"/></w:pPr><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:hint="default"/></w:rPr></w:lvl>
</w:abstractNum>
<w:abstractNum w:abstractNumId="1"><w:multiLevelType w:val="hybridMultilevel"/>
<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="420" w:hanging="420"/></w:pPr></w:lvl>
<w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2)"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="840" w:hanging="420"/></w:pPr></w:lvl>
<w:lvl w:ilvl="2"><w:start w:val="1"/><w:numFmt w:val="lowerRoman"/><w:lvlText w:val="%3."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="1260" w:hanging="420"/></w:pPr></w:lvl>
</w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
<w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>"""
}
