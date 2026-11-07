package io.github.lazy.dsh.core.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeExportTest {
    @Test
    fun theTitleComesFromTheFirstHeading() {
        assertEquals("季度报告", OfficeExport.titleOf("# 季度报告\n\n正文"))
    }

    @Test
    fun theTitleFallsBackToTheFirstLineAndDropsMarkup() {
        assertEquals("一句话总结", OfficeExport.titleOf("**一句话总结**\n正文"))
    }

    @Test
    fun aMarkdownRuleIsNotATitle() {
        assertEquals("真正的标题", OfficeExport.titleOf("---\n真正的标题"))
    }

    @Test
    fun blankMarkdownHasNoTitle() {
        assertNull(OfficeExport.titleOf("\n\n   \n"))
    }

    @Test
    fun aTitleIsCappedAtFortyCharacters() {
        val title = OfficeExport.titleOf("# " + "长".repeat(120))
        assertEquals(40, title!!.length)
    }

    @Test
    fun theFileNameReplacesCharactersWindowsAndAndroidReject() {
        val name = OfficeExport.fileName("a/b\\c:d*e?f\"g<h>i|j", OfficeFormat.DOCX, 0)
        assertEquals("a b c d e f g h i j.docx", name)
    }

    @Test
    fun theFileNameCollapsesWhitespaceAndKeepsTheExtension() {
        assertEquals("我的 报告.pptx", OfficeExport.fileName("  我的   报告  ", OfficeFormat.PPTX, 0))
    }

    @Test
    fun aMissingTitleUsesATimestampedName() {
        val name = OfficeExport.fileName(null, OfficeFormat.XLSX, 1_700_000_000_000)
        assertTrue(name.startsWith("DSH-"))
        assertTrue(name.endsWith(".xlsx"))
    }

    @Test
    fun sizesAreReadable() {
        assertEquals("512 B", OfficeExport.size(512))
        assertEquals("12 KB", OfficeExport.size(12_288))
        assertEquals("1.5 MB", OfficeExport.size(1_572_864))
    }

    @Test
    fun everyFormatDeclaresTheOfficialMimeType() {
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", OfficeFormat.DOCX.mime)
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", OfficeFormat.PPTX.mime)
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", OfficeFormat.XLSX.mime)
    }
}
