package io.github.lazy.dsh.feature.conversation

import io.github.lazy.dsh.core.data.PerformanceMode
import io.github.lazy.dsh.core.data.StickerCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickersTest {
    @Test
    fun `each mode owns its character artwork and the packs never share an id or a mood`() {
        assertEquals(11, StickerCatalog.electric.size)
        assertEquals(13, StickerCatalog.leaf.size)
        assertEquals(24, StickerCatalog.all.size)
        assertTrue(StickerCatalog.electric.all { it.character == PerformanceMode.Electric })
        assertTrue(StickerCatalog.leaf.all { it.character == PerformanceMode.Leaf })
        // forMode is the single source of "which pack does this mode wear", so a mode can never see the other.
        assertEquals(StickerCatalog.electric, StickerCatalog.forMode(PerformanceMode.Electric))
        assertEquals(StickerCatalog.leaf, StickerCatalog.forMode(PerformanceMode.Leaf))
        assertEquals(24, StickerCatalog.all.map { it.id }.toSet().size)
        // A bare `[表情: 开心]` must be unambiguous, so no mood word repeats across the packs.
        assertEquals(24, StickerCatalog.all.map { it.mood }.toSet().size)
    }

    @Test
    fun `wrong character tags stay readable and normal punctuation is preserved`() {
        val wrong = "[表情: 电荷猫·兴奋]"
        assertEquals(emptyList<Any>() to wrong, StickerCatalog.parse(wrong))
        assertEquals(emptyList<Any>() to "注意：", StickerCatalog.parse("注意："))
        assertEquals(emptyList<Any>() to "数组[", StickerCatalog.parse("数组["))
        assertEquals(emptyList<Any>() to "好了", StickerCatalog.parse("好了 [表"))
        assertEquals("sticker_01", StickerCatalog.parse("：sticker_01：").first.single().id)
        assertEquals(PerformanceMode.Leaf, StickerCatalog.byId("sticker_16")?.character)
        assertEquals("欢笑", StickerCatalog.byId("sticker_14")?.mood)
    }

    @Test
    fun `parse extracts full tags and shortcodes, leaving the prose`() {
        val (s1, text1) = StickerCatalog.parse("[表情: 电荷猫·开心]")
        assertEquals(1, s1.size)
        assertEquals("sticker_01", s1.first().id)
        assertTrue(text1.isEmpty())

        val (s2, text2) = StickerCatalog.parse("早上好呀 [表情: 叶绿精灵·兴奋] :sticker_16:")
        assertEquals(2, s2.size)
        assertEquals("sticker_07", s2[0].id)
        assertEquals("sticker_16", s2[1].id)
        assertEquals("早上好呀", text2)
    }

    @Test
    fun `a bare mood is enough, and an unfinished tag stays hidden while streaming`() {
        val (s, text) = StickerCatalog.parse("修好了 [表情: 得意]")
        assertEquals("sticker_13", s.single().id)
        assertEquals("修好了", text)

        val (none, streaming) = StickerCatalog.parse("修好了 [表情: 得")
        assertTrue(none.isEmpty())
        assertEquals("修好了", streaming)
    }

    @Test
    fun `the streaming model is only taught its own pack`() {
        val electric = StickerCatalog.prompt(PerformanceMode.Electric)
        assertTrue(electric.contains("[表情: 电荷猫·开心]"))
        assertTrue(electric.contains("开心"))
        assertFalse(electric.contains("叶绿精灵"))

        val leaf = StickerCatalog.prompt(PerformanceMode.Leaf)
        assertTrue(leaf.contains("[表情: 叶绿精灵·兴奋]"))
        assertTrue(leaf.contains("摘星"))
        assertFalse(leaf.contains("电荷猫"))

        val mode = io.github.lazy.dsh.core.model.SessionModes.Default
        assertTrue(PerformanceMode.Electric.applyTo(mode).prompt.contains(electric))
        assertTrue(PerformanceMode.Leaf.applyTo(mode).prompt.contains(leaf))

        // Appended to every turn's system prompt, so each stays a hint rather than a catalogue dump.
        assertTrue(electric.length < 700)
        assertTrue(leaf.length < 700)
    }
}
