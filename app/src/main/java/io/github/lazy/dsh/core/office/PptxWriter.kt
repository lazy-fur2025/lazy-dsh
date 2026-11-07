package io.github.lazy.dsh.core.office

/** One slide: a title and a body of lines. An empty body is the title slide. */
data class Slide(val title: String, val lines: List<String> = emptyList(), val subtitle: String? = null)

/**
 * Writes a PowerPoint deck (.pptx).
 *
 * Prose is turned into slides rather than a wall of text: the first slide is the title, every H1/H2
 * starts a slide, deeper headings become bold lines, and a body that would overflow moves to a
 * continuation slide. The deck uses one blank layout and draws every shape itself, so it renders the
 * same in PowerPoint, WPS and Keynote.
 */
object PptxWriter {
    private const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val SLIDE_W = 12192000L
    private const val SLIDE_H = 6858000L
    private const val MARGIN = 838200L
    private const val MAX_LINES = 7

    fun build(blocks: List<Block>, title: String?): ByteArray {
        val slides = plan(blocks, title)
        val entries = mutableListOf<Pair<String, String>>()
        entries += "[Content_Types].xml" to Xml.doc(contentTypes(slides.size))
        entries += "_rels/.rels" to Xml.doc(
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="$NS_R/officeDocument" Target="ppt/presentation.xml"/>""" +
                "</Relationships>",
        )
        entries += "ppt/presentation.xml" to Xml.doc(presentation(slides.size))
        entries += "ppt/_rels/presentation.xml.rels" to Xml.doc(presentationRels(slides.size))
        entries += "ppt/slideMasters/slideMaster1.xml" to Xml.doc(master())
        entries += "ppt/slideMasters/_rels/slideMaster1.xml.rels" to Xml.doc(
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="$NS_R/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>""" +
                """<Relationship Id="rId2" Type="$NS_R/theme" Target="../theme/theme1.xml"/>""" +
                "</Relationships>",
        )
        entries += "ppt/slideLayouts/slideLayout1.xml" to Xml.doc(layout())
        entries += "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to Xml.doc(
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="$NS_R/slideMaster" Target="../slideMasters/slideMaster1.xml"/>""" +
                "</Relationships>",
        )
        entries += "ppt/theme/theme1.xml" to Xml.doc(theme())
        slides.forEachIndexed { i, slide ->
            entries += "ppt/slides/slide${i + 1}.xml" to Xml.doc(slideXml(slide))
            entries += "ppt/slides/_rels/slide${i + 1}.xml.rels" to Xml.doc(
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="$NS_R/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>""" +
                    "</Relationships>",
            )
        }
        return zipOf(entries)
    }

    /** Turns blocks into slides; always at least one. */
    fun plan(blocks: List<Block>, title: String?): List<Slide> {
        val firstHeading = blocks.firstOrNull() as? Block.Heading
        val docTitle = title?.takeIf { it.isNotBlank() }
            ?: firstHeading?.let { MarkdownBlocks.plain(it.spans) }?.takeIf { it.isNotBlank() }
            ?: "演示文稿"
        val lead = blocks.firstOrNull { it is Block.Paragraph }?.let { MarkdownBlocks.plain((it as Block.Paragraph).spans) }
        val out = mutableListOf<Slide>()
        out += Slide(docTitle, subtitle = lead?.takeIf { it.isNotBlank() })
        // The heading that named the deck is not repeated as a content slide.
        val body = if (title == null && firstHeading != null && MarkdownBlocks.plain(firstHeading.spans) == docTitle) {
            blocks.drop(1)
        } else {
            blocks
        }

        var current: Slide? = null
        val lines = mutableListOf<String>()
        fun flush(final: Boolean = false) {
            val slide = current ?: return
            if (lines.isEmpty()) {
                // A heading with nothing under it yet is a title, not a slide: the next heading
                // replaces it. Only the last one survives as a section divider.
                if (final) out += slide
                return
            }
            lines.chunked(MAX_LINES).forEachIndexed { i, chunk ->
                out += Slide(if (i == 0) slide.title else "${slide.title}（续）", chunk)
            }
            lines.clear()
        }
        // The paragraph used as the title slide's subtitle is not repeated on a content slide.
        val skipLead = lead
        var leadConsumed = false
        for (block in body) {
            when (block) {
                is Block.Heading -> {
                    if (block.level <= 2) {
                        flush()
                        current = Slide(MarkdownBlocks.plain(block.spans).ifBlank { docTitle })
                    } else {
                        if (current == null) current = Slide(docTitle)
                        lines += MarkdownBlocks.plain(block.spans)
                    }
                }
                is Block.Paragraph -> {
                    val text = MarkdownBlocks.plain(block.spans)
                    if (!leadConsumed && text == skipLead) { leadConsumed = true; continue }
                    if (text.isNotBlank()) {
                        if (current == null) current = Slide(docTitle)
                        lines += text
                    }
                }
                is Block.Bullet -> {
                    if (current == null) current = Slide(docTitle)
                    lines += "• " + MarkdownBlocks.plain(block.spans)
                }
                is Block.Numbered -> {
                    if (current == null) current = Slide(docTitle)
                    lines += "• " + MarkdownBlocks.plain(block.spans)
                }
                is Block.Quote -> {
                    if (current == null) current = Slide(docTitle)
                    lines += "「" + MarkdownBlocks.plain(block.spans) + "」"
                }
                is Block.Code -> {
                    if (current == null) current = Slide(docTitle)
                    lines += block.text.split('\n').take(4)
                }
                is Block.Table -> {
                    if (current == null) current = Slide(docTitle)
                    lines += block.header.joinToString("  |  ")
                    block.rows.take(4).forEach { lines += it.joinToString("  |  ") }
                }
                Block.Rule -> Unit
            }
        }
        flush(final = true)
        return out.ifEmpty { listOf(Slide(docTitle)) }
    }

    private fun contentTypes(slideCount: Int): String = buildString {
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
        append("""<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""")
        append("""<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""")
        append("""<Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>""")
        for (i in 1..slideCount) {
            append("""<Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        }
        append("</Types>")
    }

    private fun presentation(slideCount: Int): String = buildString {
        append("""<p:presentation xmlns:a="$NS_A" xmlns:r="$NS_R" xmlns:p="$NS_P">""")
        append("""<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>""")
        append("<p:sldIdLst>")
        for (i in 1..slideCount) append("""<p:sldId id="${255 + i}" r:id="rId${i + 1}"/>""")
        append("</p:sldIdLst>")
        append("""<p:sldSz cx="$SLIDE_W" cy="$SLIDE_H"/><p:notesSz cx="6858000" cy="9144000"/>""")
        append("</p:presentation>")
    }

    private fun presentationRels(slideCount: Int): String = buildString {
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="$NS_R/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
        for (i in 1..slideCount) {
            append("""<Relationship Id="rId${i + 1}" Type="$NS_R/slide" Target="slides/slide$i.xml"/>""")
        }
        append("</Relationships>")
    }

    private fun master(): String = """<p:sldMaster xmlns:a="$NS_A" xmlns:r="$NS_R" xmlns:p="$NS_P">
<p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>
<p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
</p:spTree></p:cSld>
<p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
<p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
</p:sldMaster>"""

    private fun layout(): String = """<p:sldLayout xmlns:a="$NS_A" xmlns:r="$NS_R" xmlns:p="$NS_P" type="blank" preserve="1">
<p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"""

    private fun theme(): String = """<a:theme xmlns:a="$NS_A" name="DSH">
<a:themeElements>
<a:clrScheme name="DSH"><a:dk1><a:srgbClr val="1A1A1A"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="2B2C33"/></a:dk2><a:lt2><a:srgbClr val="F4F6FB"/></a:lt2><a:accent1><a:srgbClr val="4176E6"/></a:accent1><a:accent2><a:srgbClr val="7C5CFF"/></a:accent2><a:accent3><a:srgbClr val="0EA371"/></a:accent3><a:accent4><a:srgbClr val="F59E0B"/></a:accent4><a:accent5><a:srgbClr val="12B886"/></a:accent5><a:accent6><a:srgbClr val="D6336C"/></a:accent6><a:hlink><a:srgbClr val="4176E6"/></a:hlink><a:folHlink><a:srgbClr val="7C5CFF"/></a:folHlink></a:clrScheme>
<a:fontScheme name="DSH"><a:majorFont><a:latin typeface="Calibri"/><a:ea typeface="Microsoft YaHei"/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/><a:ea typeface="Microsoft YaHei"/><a:cs typeface=""/></a:minorFont></a:fontScheme>
<a:fmtScheme name="DSH">
<a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst>
<a:lnStyleLst><a:ln w="6350"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln w="19050"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst>
<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>
<a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst>
</a:fmtScheme>
</a:themeElements></a:theme>"""

    private fun slideXml(slide: Slide): String = buildString {
        append("""<p:sld xmlns:a="$NS_A" xmlns:r="$NS_R" xmlns:p="$NS_P"><p:cSld><p:spTree>""")
        append("""<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>""")
        append("""<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>""")
        if (slide.subtitle != null && slide.lines.isEmpty()) {
            val subtitle = slide.subtitle
            // Title slide: the title sits in the middle third.
            append(textBox(2, "Title", MARGIN, 1825625, SLIDE_W - MARGIN * 2, 1600000,
                listOf(Para(slide.title, 4000, bold = true, align = "l")), anchor = "b"))
            append(textBox(3, "Subtitle", MARGIN, 3500000, SLIDE_W - MARGIN * 2, 1400000,
                listOf(Para(subtitle.take(160), 1800, color = "475467", align = "l")), anchor = "t"))
        } else {
            append(textBox(2, "Title", MARGIN, 457200, SLIDE_W - MARGIN * 2, 1100000,
                listOf(Para(slide.title, 2800, bold = true, align = "l")), anchor = "b"))
            append("""<p:sp><p:nvSpPr><p:cNvPr id="3" name="Rule"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>""")
            append("""<p:spPr><a:xfrm><a:off x="$MARGIN" y="1600000"/><a:ext cx="${SLIDE_W - MARGIN * 2}" cy="28575"/></a:xfrm>""")
            append("""<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="4176E6"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>""")
            if (slide.lines.isNotEmpty()) {
                append(textBox(4, "Body", MARGIN, 1778000, SLIDE_W - MARGIN * 2, 4400000,
                    slide.lines.map { Para(it, 1600, bullet = true) }, anchor = "t"))
            }
        }
        append("</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>")
    }

    private data class Para(
        val text: String,
        val size: Int,
        val bold: Boolean = false,
        val bullet: Boolean = false,
        val color: String? = null,
        val align: String = "l",
    )

    private fun textBox(id: Int, name: String, x: Long, y: Long, cx: Long, cy: Long, paras: List<Para>, anchor: String): String = buildString {
        append("""<p:sp><p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>""")
        append("""<p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom>""")
        append("""<a:noFill/><a:ln><a:noFill/></a:ln></p:spPr>""")
        append("""<p:txBody><a:bodyPr wrap="square" anchor="$anchor"><a:normAutofit/></a:bodyPr><a:lstStyle/>""")
        // CT_TextBody needs at least one <a:p>; an empty body makes PowerPoint offer a repair.
        if (paras.isEmpty()) append("""<a:p><a:endParaRPr lang="zh-CN"/></a:p>""")
        for (p in paras) {
            append("<a:p>")
            if (p.bullet) {
                append("""<a:pPr marL="285750" lvl="0" algn="${p.align}"><a:lnSpc><a:spcPct val="105000"/></a:lnSpc>""")
                append("""<a:spcBef><a:spcPts val="600"/></a:spcBef><a:buFont typeface="Arial"/><a:buChar char="•"/></a:pPr>""")
            } else {
                append("""<a:pPr algn="${p.align}"><a:lnSpc><a:spcPct val="100000"/></a:lnSpc>""")
                append("""<a:spcBef><a:spcPts val="300"/></a:spcBef><a:buNone/></a:pPr>""")
            }
            append("""<a:r><a:rPr lang="zh-CN" altLang="en-US" sz="${p.size}" b="${if (p.bold) 1 else 0}" dirty="0">""")
            if (p.color != null) append("""<a:solidFill><a:srgbClr val="${p.color}"/></a:solidFill>""")
            append("</a:rPr>")
            append("""<a:t>${Xml.esc(p.text)}</a:t></a:r></a:p>""")
        }
        append("</p:txBody></p:sp>")
    }
}
