package io.github.lazy.dsh.core.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {
    @Test
    fun headingsKeepTheirLevelAndInlineFormatting() {
        val blocks = MarkdownBlocks.parse("## 数据 **重点**")
        val heading = blocks.single() as Block.Heading
        assertEquals(2, heading.level)
        assertEquals("数据 重点", MarkdownBlocks.plain(heading.spans))
        assertTrue("the bold run keeps its flag", heading.spans.any { it.bold && it.text == "重点" })
    }

    @Test
    fun aFencedBlockKeepsItsLinesEvenWhenTheyLookLikeMarkdown() {
        val md = "```kotlin\nval x = 1\n# not a heading\n- not a list\n```"
        val code = MarkdownBlocks.parse(md).single() as Block.Code
        assertEquals("kotlin", code.lang)
        assertEquals("val x = 1\n# not a heading\n- not a list", code.text)
    }

    @Test
    fun anUnclosedFenceStillYieldsCode() {
        val code = MarkdownBlocks.parse("```\nline one\nline two").single() as Block.Code
        assertEquals("line one\nline two", code.text)
    }

    @Test
    fun aPipeTableIsSplitIntoHeaderAndRows() {
        val md = "| 指标 | 数值 |\n| --- | ---: |\n| 收入 | 120 |\n| 成本 | 80 |"
        val table = MarkdownBlocks.parse(md).single() as Block.Table
        assertEquals(listOf("指标", "数值"), table.header)
        assertEquals(listOf(listOf("收入", "120"), listOf("成本", "80")), table.rows)
    }

    @Test
    fun aTableWithoutOuterPipesStillParses() {
        val table = MarkdownBlocks.parse("a | b\n--- | ---\n1 | 2").single() as Block.Table
        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(listOf("1", "2")), table.rows)
    }

    @Test
    fun bulletsAndNumbersKeepTheirDepth() {
        val blocks = MarkdownBlocks.parse("- one\n  - nested\n1. first")
        assertEquals(Block.Bullet(listOf(Span("one")), 0), blocks[0])
        assertEquals(Block.Bullet(listOf(Span("nested")), 1), blocks[1])
        assertEquals(Block.Numbered(listOf(Span("first")), 0), blocks[2])
    }

    @Test
    fun aDashRuleIsNotABullet() {
        assertEquals(listOf<Block>(Block.Rule), MarkdownBlocks.parse("- - -"))
    }

    @Test
    fun aParagraphJoinsWrappedLines() {
        val para = MarkdownBlocks.parse("第一行\n第二行\n\n另一段").first() as Block.Paragraph
        assertEquals("第一行 第二行", MarkdownBlocks.plain(para.spans))
    }

    @Test
    fun inlineCodeBoldItalicAndLinks() {
        val spans = MarkdownBlocks.inline("**粗** *斜* `码` [链接](https://x.y)")
        assertEquals("粗", spans.first { it.bold }.text)
        assertEquals("斜", spans.first { it.italic }.text)
        assertEquals("码", spans.first { it.code }.text)
        val plain = MarkdownBlocks.plain(spans)
        assertTrue(plain.contains("链接"))
        assertTrue("the URL is dropped, the label kept", !plain.contains("https://x.y"))
    }

    @Test
    fun snakeCaseIsNotItalic() {
        val spans = MarkdownBlocks.inline("snake_case_name")
        assertEquals("snake_case_name", MarkdownBlocks.plain(spans))
        assertTrue(spans.none { it.italic })
    }

    @Test
    fun aQuoteMergesItsLines() {
        val quote = MarkdownBlocks.parse("> 第一句\n> 第二句").single() as Block.Quote
        assertEquals("第一句 第二句", MarkdownBlocks.plain(quote.spans))
    }
}
