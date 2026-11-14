package io.github.lazy.dsh.core.interactive

/**
 * Some Android WebViews resolve CSS viewport units to zero for a document loaded with
 * `loadDataWithBaseURL` (the layout viewport is not ready when the parser sees `vh`), even though
 * `window.innerHeight` is correct afterwards. AI-written scenes use `100vh`/`74vh` constantly, so
 * bake the current viewport into pixels before loading. A rotation or resize replays it.
 */
object ViewportUnits {
    private val VH = Regex("""(\d+(?:\.\d+)?)(?:dvh|svh|lvh|vh)\b""")
    private val VW = Regex("""(\d+(?:\.\d+)?)(?:dvw|svw|lvw|vw)\b""")

    /** [html] with `vh`/`vw` (including the dvh/svh/lvh variants) replaced by concrete pixels. */
    fun rewrite(html: String, width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return html
        val withHeight = VH.replace(html) { m ->
            "${(m.groupValues[1].toFloat() * height / 100f).toInt()}px"
        }
        return VW.replace(withHeight) { m ->
            "${(m.groupValues[1].toFloat() * width / 100f).toInt()}px"
        }
    }
}
