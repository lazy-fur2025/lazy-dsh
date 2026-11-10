package io.github.lazy.dsh.core.data

import androidx.annotation.DrawableRes
import io.github.lazy.dsh.R

/**
 * 表情包 — the character sticker packs the **assistant** sends, cut from `pictures/表情集.png`.
 *
 * Two packs, one per 能耗模式: 电荷 mode wears ⚡电荷猫, 叶绿 mode wears 🍃叶绿精灵.
 * The model never sees the PNGs: it writes a tag (`[表情: 电荷猫·开心]`,
 * or the short mood form `[表情: 开心]`) at the end of a reply and the chat renders the artwork.
 * [prompt] teaches it only its own pack ([PerformanceMode.applyTo]); [parse] is deliberately
 * unaware of modes so a logged reply from either mode still renders.
 */
data class StickerDef(
    val id: String,
    @DrawableRes val res: Int,
    val character: PerformanceMode,
    /** The mood in Chinese: the word the model writes in the tag, and the gallery's caption. */
    val mood: String,
    /** One emoji for the tag line, so the gallery reads at a glance. */
    val emoji: String,
) {
    val characterZh: String get() = if (character == PerformanceMode.Electric) "电荷猫" else "叶绿精灵"

    /** The tag the model writes and the app renders. */
    val tag: String get() = "[表情: $characterZh·$mood]"

    /** A compact ASCII form, in case a model mangles the brackets around CJK text. */
    val shortCode: String get() = ":$id:"
}

object StickerCatalog {
    /** ⚡ 电荷猫 — the 电荷 mode's pack, grouped by the actual character artwork. */
    val electric: List<StickerDef> = listOf(
        // Row 1 of the sheet: waving hello, calm gaze, shock, anger, sleep, expectant sparkle.
        StickerDef("sticker_01", R.drawable.sticker_01, PerformanceMode.Electric, "开心", "😸"),
        StickerDef("sticker_02", R.drawable.sticker_02, PerformanceMode.Electric, "淡定", "😌"),
        StickerDef("sticker_03", R.drawable.sticker_03, PerformanceMode.Electric, "惊讶", "🙀"),
        StickerDef("sticker_04", R.drawable.sticker_04, PerformanceMode.Electric, "生气", "😾"),
        StickerDef("sticker_05", R.drawable.sticker_05, PerformanceMode.Electric, "困倦", "😪"),
        StickerDef("sticker_06", R.drawable.sticker_06, PerformanceMode.Electric, "期待", "✨"),
        // Row 3: smug, laughing, sighing, cheering, puzzled; the green star face belongs to Leaf.
        StickerDef("sticker_13", R.drawable.sticker_13, PerformanceMode.Electric, "得意", "😼"),
        StickerDef("sticker_14", R.drawable.sticker_14, PerformanceMode.Electric, "欢笑", "😆"),
        StickerDef("sticker_15", R.drawable.sticker_15, PerformanceMode.Electric, "无奈", "😑"),
        StickerDef("sticker_17", R.drawable.sticker_17, PerformanceMode.Electric, "大笑", "😹"),
        StickerDef("sticker_18", R.drawable.sticker_18, PerformanceMode.Electric, "疑惑", "🤔"),
    )

    /** 🍃 叶绿精灵 — the 叶绿 mode's pack, including the green star face. */
    val leaf: List<StickerDef> = listOf(
        // Row 2 of the sheet: excited, warm, curious, tears, peaceful, glancing away.
        StickerDef("sticker_07", R.drawable.sticker_07, PerformanceMode.Leaf, "兴奋", "🤩"),
        StickerDef("sticker_08", R.drawable.sticker_08, PerformanceMode.Leaf, "欣慰", "😊"),
        StickerDef("sticker_09", R.drawable.sticker_09, PerformanceMode.Leaf, "好奇", "🌱"),
        StickerDef("sticker_10", R.drawable.sticker_10, PerformanceMode.Leaf, "泪目", "💧"),
        StickerDef("sticker_11", R.drawable.sticker_11, PerformanceMode.Leaf, "打盹", "💤"),
        StickerDef("sticker_12", R.drawable.sticker_12, PerformanceMode.Leaf, "偷看", "🎵"),
        StickerDef("sticker_16", R.drawable.sticker_16, PerformanceMode.Leaf, "摘星", "⭐"),
        // Row 4: cheering, well-behaved, thinking, blissful, content, shy.
        StickerDef("sticker_19", R.drawable.sticker_19, PerformanceMode.Leaf, "欢呼", "🙌"),
        StickerDef("sticker_20", R.drawable.sticker_20, PerformanceMode.Leaf, "乖巧", "🥺"),
        StickerDef("sticker_21", R.drawable.sticker_21, PerformanceMode.Leaf, "思考", "💭"),
        StickerDef("sticker_22", R.drawable.sticker_22, PerformanceMode.Leaf, "幸福", "🌸"),
        StickerDef("sticker_23", R.drawable.sticker_23, PerformanceMode.Leaf, "熟睡", "😴"),
        StickerDef("sticker_24", R.drawable.sticker_24, PerformanceMode.Leaf, "转身", "🎵"),
    )

    val all: List<StickerDef> = electric + leaf

    private val byId = all.associateBy { it.id }
    private val byMood = all.associateBy { it.mood }

    /** The faces of [mode]'s own pack. */
    fun forMode(mode: PerformanceMode?): List<StickerDef> = when (mode) {
        PerformanceMode.Leaf -> leaf
        else -> electric
    }

    fun byId(id: String): StickerDef? = byId[id]

    /**
     * The model-facing instruction for [mode]'s own pack. Kept to a few lines: it rides in every
     * turn's system prompt, and 叶绿 mode exists to spend as little as possible.
     */
    fun prompt(mode: PerformanceMode): String {
        val pack = forMode(mode)
        val character = pack.first().characterZh
        val peek = pack.first()
        return buildString {
            append("## 表情包 (stickers you can send)\n")
            append("You are ").append(character).append(", and this mode's pack is ").append(character).append("'s ").append(pack.size).append(" faces — never the other character's. ")
            append("To send one, put its tag on its own line at the very end of a reply: ").append(peek.tag)
            append(" (or just `[表情: ").append(peek.mood).append("]`).\n")
            append("Faces: ").append(pack.joinToString(" ") { it.mood }).append(".\n")
            append("Send at most one per reply, and only when a face carries what the words already mean — a greeting, a win, an apology, sympathy, a shared joke. ")
            append("Never instead of the answer, never two, never explain the tag.")
        }
    }

    private val tagRegex = Regex("""\[\s*表情\s*[:：]\s*([^\]\n]+?)\s*\]|：(sticker_\d{2})：|:(sticker_\d{2}):""")

    /**
     * The stickers a message carries and the text left without their tags. A tag written but not
     * yet closed (the reply is still streaming) is hidden rather than shown as raw text.
     */
    fun parse(text: String): Pair<List<StickerDef>, String> {
        val found = mutableListOf<StickerDef>()
        val cleaned = tagRegex.replace(text) { m ->
            val label = m.groups[1]?.value?.trim()
            val mood = label?.substringAfter('·')?.trim()
            val character = label?.takeIf { '·' in it }?.substringBefore('·')?.trim()
            val id = m.groups[2]?.value ?: m.groups[3]?.value
            val sticker = when {
                id != null -> byId[id]
                mood != null -> byMood[mood]?.takeIf { character == null || it.characterZh == character }
                else -> null
            }
            if (sticker != null) { found += sticker; "" } else m.value
        }
        return found to dropOpenTag(cleaned).trim()
    }

    /** Hides a tag the model is still writing (`… [表情: 开`), so a streaming reply never flashes it. */
    private fun dropOpenTag(text: String): String {
        val open = text.lastIndexOf('[')
        if (open < 0) return text
        val tail = text.substring(open)
        if (tail.contains(']')) return text
        val head = tail.removePrefix("[").trimStart()
        val isTag = head == "表" || head.startsWith("表情")
        return if (isTag) text.substring(0, open) else text
    }
}
