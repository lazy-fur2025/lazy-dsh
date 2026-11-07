package io.github.lazy.dsh.core.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The three writers are only useful if the archive is actually openable. These tests unzip every
 * part and parse it, which is the closest a JVM test gets to "Word opened this file".
 */
class OfficeWritersTest {
    private val markdown = """
        # 季度报告

        这是**摘要**，包含 `code`、<尖括号> & 与 [链接](https://example.com)。

        ## 数据

        | 指标 | 数值 |
        | --- | --- |
        | 收入 | 120 |
        | 成本 | 80 |

        - 第一点
        - 第二点

        ```kotlin
        val x = 1 < 2 && 3 > 2
        ```

        > 一句引用
    """.trimIndent()

    private fun parts(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                out[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun assertWellFormed(name: String, xml: String) {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        try {
            builder.parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            fail("$name is not well-formed XML: ${e.message}\n${xml.take(400)}")
        }
    }

    private fun assertAllPartsWellFormed(bytes: ByteArray, label: String) {
        val entries = parts(bytes)
        assertTrue("$label has no parts", entries.isNotEmpty())
        entries.forEach { (name, body) -> assertWellFormed("$label/$name", body) }
    }

    @Test
    fun everyPartOfEveryFormatIsWellFormedXml() {
        OfficeFormat.entries.forEach { format ->
            val bytes = OfficeExport.build(markdown, format)
            assertAllPartsWellFormed(bytes, format.extension)
        }
    }

    @Test
    fun aDocxCarriesTheExpectedParts() {
        val entries = parts(OfficeExport.build(markdown, OfficeFormat.DOCX))
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels", "word/styles.xml", "word/numbering.xml"),
            entries.keys,
        )
        assertTrue(entries.getValue("[Content_Types].xml").contains("wordprocessingml.document.main"))
    }

    @Test
    fun aDocxKeepsTextFormattingListsTablesAndCode() {
        val doc = parts(OfficeExport.build(markdown, OfficeFormat.DOCX)).getValue("word/document.xml")
        assertTrue("title", doc.contains("季度报告"))
        assertTrue("bold run", doc.contains("<w:b/>"))
        assertTrue("monospace run", doc.contains("Consolas"))
        assertTrue("numbered/bulleted list", doc.contains("<w:numPr>"))
        assertTrue("table", doc.contains("<w:tbl>"))
        assertTrue("code block shading", doc.contains("F3F4F6"))
        assertTrue("escaped angle brackets", doc.contains("&lt;尖括号&gt;"))
        assertTrue("escaped ampersand", doc.contains("&amp;"))
    }

    @Test
    fun aPptxHasOneContentSlidePerHeading() {
        val slides = PptxWriter.plan(MarkdownBlocks.parse(markdown), "季度报告")
        // Title slide + 数据 (+ its lists/table/code/quote all on the 数据 slide).
        assertEquals(2, slides.size)
        assertEquals("季度报告", slides[0].title)
        assertEquals("数据", slides[1].title)
        assertTrue(slides[1].lines.any { it.contains("收入") })
    }

    @Test
    fun aPptxTitleSlideTakesTheFirstParagraphAsSubtitle() {
        val slides = PptxWriter.plan(MarkdownBlocks.parse(markdown), "季度报告")
        assertTrue(slides[0].subtitle!!.contains("摘要"))
        assertTrue("the subtitle is not repeated on a content slide", slides.drop(1).none { s -> s.lines.any { it.contains("摘要") } })
    }

    @Test
    fun aPptxSplitsAnOverlongBodyAcrossSlides() {
        val md = "## 长文\n" + (1..20).joinToString("\n") { "- 第 $it 行" }
        val slides = PptxWriter.plan(MarkdownBlocks.parse(md), null)
        assertTrue("the body is split", slides.size >= 3)
        assertTrue(slides.any { it.title.endsWith("（续）") })
    }

    @Test
    fun anEmptyMarkdownStillProducesAOneSlideDeck() {
        val slides = PptxWriter.plan(emptyList(), null)
        assertEquals(1, slides.size)
    }

    @Test
    fun anEmptyOrHeadingsOnlyDocumentIsStillWellFormed() {
        for (md in listOf("", "# 只有标题", "# 标题\n## 第二节")) {
            OfficeFormat.entries.forEach { format ->
                assertAllPartsWellFormed(OfficeExport.build(md, format), "${format.extension}/$md")
            }
        }
    }

    @Test
    fun aSlideWithoutABodyStillCarriesAParagraph() {
        // CT_TextBody requires <a:p>+: a text box with none makes PowerPoint repair the file.
        val slide = parts(OfficeExport.build("", OfficeFormat.PPTX)).getValue("ppt/slides/slide1.xml")
        assertTrue(slide.contains("<p:txBody>"))
        assertTrue(slide.contains("<a:p>") || slide.contains("<a:p/>"))
    }

    @Test
    fun aPptxCarriesTheExpectedParts() {
        val entries = parts(OfficeExport.build(markdown, OfficeFormat.PPTX))
        assertTrue(entries.containsKey("ppt/presentation.xml"))
        assertTrue(entries.containsKey("ppt/slideMasters/slideMaster1.xml"))
        assertTrue(entries.containsKey("ppt/slideLayouts/slideLayout1.xml"))
        assertTrue(entries.containsKey("ppt/theme/theme1.xml"))
        assertTrue(entries.containsKey("ppt/slides/slide1.xml"))
        assertTrue(entries.keys.any { it.startsWith("ppt/slides/_rels/") })
    }

    @Test
    fun aTableBecomesASheetNamedAfterItsHeading() {
        val sheets = XlsxWriter.sheetsOf(MarkdownBlocks.parse(markdown), "季度报告")
        assertEquals(1, sheets.size)
        assertEquals("数据", sheets[0].name)
        assertEquals(listOf("指标", "数值"), sheets[0].rows.first())
        assertEquals(listOf("收入", "120"), sheets[0].rows[1])
    }

    @Test
    fun duplicateSheetNamesGetASuffix() {
        val md = "## 汇总\n| a |\n| --- |\n| 1 |\n## 汇总\n| b |\n| --- |\n| 2 |"
        val sheets = XlsxWriter.sheetsOf(MarkdownBlocks.parse(md), null)
        assertEquals(2, sheets.size)
        assertEquals("汇总", sheets[0].name)
        assertEquals("汇总 (2)", sheets[1].name)
    }

    @Test
    fun aWorkbookWithoutTablesStillHasOneSheet() {
        val sheets = XlsxWriter.sheetsOf(MarkdownBlocks.parse("# 标题\n一段正文"), "我的文件")
        assertEquals(1, sheets.size)
        assertEquals("我的文件", sheets[0].name)
        assertEquals(listOf("类型", "内容"), sheets[0].rows.first())
        assertTrue(sheets[0].rows.any { it == listOf("正文", "一段正文") })
    }

    @Test
    fun numbersBecomeNumericCellsAndIdentifiersStayText() {
        val md = "| id | 金额 |\n| --- | --- |\n| 007 | 12.5 |\n| 12月 | 1 |"
        val sheet = parts(OfficeExport.build(md, OfficeFormat.XLSX)).getValue("xl/worksheets/sheet1.xml")
        assertTrue("a decimal is a numeric cell", sheet.contains("<v>12.5</v>"))
        assertTrue("a leading-zero id stays text", sheet.contains(">007<"))
        assertTrue("text with units stays text", sheet.contains(">12月<"))
    }

    @Test
    fun sheetNamesAreRenderedAsXmlAttributes() {
        val md = "## A&B \"quoted\"\n| x |\n| --- |\n| 1 |"
        val workbook = parts(OfficeExport.build(md, OfficeFormat.XLSX)).getValue("xl/workbook.xml")
        assertTrue(workbook.contains("A&amp;B &quot;quoted&quot;"))
    }

    @Test
    fun controlCharactersAreDroppedInsteadOfBreakingTheFile() {
        val doc = parts(OfficeExport.build("bad\u0000\u0007text", OfficeFormat.DOCX)).getValue("word/document.xml")
        assertFalse(doc.contains('\u0000'))
        assertWellFormed("document.xml", doc)
    }

    @Test
    fun theSameInputProducesTheSameBytes() {
        OfficeFormat.entries.forEach { format ->
            assertTrue(format.name, OfficeExport.build(markdown, format).contentEquals(OfficeExport.build(markdown, format)))
        }
    }

    @Test
    fun aDocxTitleIsNotRepeatedFromTheHeading() {
        val doc = parts(OfficeExport.build("# 唯一标题", OfficeFormat.DOCX, title = "唯一标题")).getValue("word/document.xml")
        assertEquals("the title appears once", 1, Regex("唯一标题").findAll(doc).count())
    }
}
