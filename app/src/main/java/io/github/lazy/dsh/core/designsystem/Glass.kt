package io.github.lazy.dsh.core.designsystem

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

// ── The glass system ────────────────────────────────────────────────────────────────────────────
//
// Three things make a panel read as glass, in this order of importance:
//   1. the edge light (a bright top edge fading into a dark bottom edge),
//   2. a translucent fill that lets the aurora through,
//   3. a soft shadow that lifts it off the canvas.
// Blur is a fourth, optional layer: RenderEffect only exists on API 31+, and Robolectric never
// renders it, so nothing here is allowed to depend on it for the look to hold up.

/** How much of the glass fill to use. Chrome that sits over live text uses [Thin]; cards [Regular]. */
enum class GlassTier(val factor: Float) {
    /** Barely there: pass-through chrome over scrolling content. */
    Thin(0.55f),
    Regular(1f),
    /** Readability first: menus, dialogs, the drawer. */
    Thick(1.25f),
}

private fun Color.scaled(by: Float): Color = copy(alpha = (alpha * by).coerceIn(0f, 1f))

/**
 * The app background: an opaque canvas with three soft colour orbs. Painted with radial gradients
 * instead of a blur effect, so it looks the same on API 29, on API 34 and in headless previews.
 *
 * [progress] (0..1) shifts the orbs, letting a screen parallax them against scroll without
 * animating a full-screen gradient every frame.
 */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier, progress: Float = 0f, overlay: Color = Color.Transparent) {
    val c = Dsh.colors
    val quiet = LocalReduceTransparency.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.canvas)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val p = progress.coerceIn(0f, 1f)
        fun orb(color: Color, cx: Float, cy: Float, radius: Float) {
            if (color.alpha <= 0f) return
            drawRect(
                Brush.radialGradient(
                    0f to color,
                    0.42f to color.scaled(0.42f),
                    0.75f to color.scaled(0.10f),
                    1f to Color.Transparent,
                    center = Offset(cx * w, cy * h),
                    radius = radius * maxOf(w, h),
                ),
            )
        }
        // Small, distinct orbs on a mostly untouched canvas: a wash of colour reads as a wallpaper,
        // three soft lights read as depth behind glass.
        orb(c.auroraA, 0.10f + p * 0.08f, 0.10f - p * 0.04f, 0.66f)
        orb(c.auroraB, 0.96f - p * 0.10f, 0.20f + p * 0.06f, 0.58f)
        orb(c.auroraC, 0.06f + p * 0.04f, 0.56f - p * 0.06f, 0.46f)
        // A whisper of vertical shade so chrome edges stay readable where an orb is brightest.
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.62f to Color.Transparent, 1f to c.canvas.scaled(0.45f)))
        // A faint drafting grid gives the light field structure without competing with text. It fades
        // out below the chrome, so a long reply is read on a clean surface rather than through graph paper.
        if (!quiet) {
            val step = 32.dp.toPx()
            val base = c.brandGradStart.copy(alpha = if (c.isDark) 0.05f else 0.038f)
            val fade = Brush.verticalGradient(
                0f to base, 0.42f to base, 0.72f to Color.Transparent,
                startY = 0f, endY = h,
            )
            var x = 0f
            while (x < w) {
                drawLine(fade, Offset(x, 0f), Offset(x, h), 0.5.dp.toPx())
                x += step
            }
            var y = 0f
            while (y < h) {
                drawLine(fade, Offset(0f, y), Offset(w, y), 0.5.dp.toPx())
                y += step
            }
        }
        if (overlay.alpha > 0f) drawRect(overlay)
    }
}

/** A bounded optical motif for the empty state; no perpetual redraw or simulated status lights. */
@Composable
fun OrbitalMark(modifier: Modifier = Modifier) {
    val c = Dsh.colors
    val quiet = LocalReduceTransparency.current
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.5f
            if (!quiet) drawCircle(
                Brush.radialGradient(listOf(c.brandGradStart.copy(alpha = 0.25f), Color.Transparent), center, r),
                r,
            )
            val inner = r * 0.46f
            val outer = r * 0.75f
            drawCircle(c.business.copy(alpha = 0.20f), inner, style = Stroke(0.5.dp.toPx()))
            drawCircle(c.business.copy(alpha = 0.12f), outer, style = Stroke(0.5.dp.toPx()))
            val brush = Brush.sweepGradient(listOf(c.brandGradStart, c.brandGradEnd, c.brandGradStart))
            drawArc(brush, -65f, 112f, false, Offset(center.x - outer, center.y - outer),
                Size(outer * 2, outer * 2), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            drawArc(brush, 112f, 42f, false, Offset(center.x - inner, center.y - inner),
                Size(inner * 2, inner * 2), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            for (i in 0 until 24) {
                val a = i * kotlin.math.PI / 12
                val start = Offset(center.x + kotlin.math.cos(a).toFloat() * r * 0.90f,
                    center.y + kotlin.math.sin(a).toFloat() * r * 0.90f)
                val end = Offset(center.x + kotlin.math.cos(a).toFloat() * r * 0.94f,
                    center.y + kotlin.math.sin(a).toFloat() * r * 0.94f)
                drawLine(c.business.copy(alpha = 0.28f), start, end, 0.5.dp.toPx())
            }
        }
        Whale(width = 36.dp, tint = c.labelPrimary)
    }
}

/**
 * Glass surface: shadow, translucent gradient fill, and the edge light. [tier] scales the fill,
 * [elevation] the shadow (0 = flush with the page).
 */
@Composable
fun Modifier.frost(
    shape: Shape = Radius.lg,
    tier: GlassTier = GlassTier.Regular,
    elevation: Dp = 10.dp,
    edge: Boolean = true,
): Modifier {
    val c = Dsh.colors
    val f = tier.factor * if (c.isDark) 1f else 0.74f
    var m = this
    if (elevation > 0.dp) m = m.shadow(elevation, shape, ambientColor = c.glassShadow, spotColor = c.glassShadow)
    m = m.clip(shape).background(
        Brush.verticalGradient(
            0f to c.glassFillTop.scaled(f),
            0.55f to c.glassFillBottom.scaled(f),
            1f to c.glassFillBottom.scaled(f * 0.7f),
        ),
    )
    if (edge) m = m.border(
        Hairline,
        Brush.verticalGradient(0f to c.glassStrokeTop.scaled(f.coerceAtMost(1.1f)), 1f to c.glassStrokeBottom),
        shape,
    )
    return m
}

/** Edge light alone, for surfaces that already carry their own fill (bubbles, tool rows). */
@Composable
fun Modifier.edgeLight(shape: Shape, strength: Float = 1f): Modifier {
    val c = Dsh.colors
    return border(
        Hairline,
        Brush.verticalGradient(0f to c.glassStrokeTop.scaled(strength), 1f to c.glassStrokeBottom.scaled(strength)),
        shape,
    )
}

/**
 * Real backdrop blur for a layer's own content (the conversation while the drawer is open, an
 * image under a caption). No-op below API 31 and when the user turned blur off.
 */
@Composable
fun Modifier.glassBlur(radius: Dp = 16.dp): Modifier =
    if (LocalFrostEnabled.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(radius, BlurredEdgeTreatment.Unbounded) else this

/**
 * Blurs whatever is behind a dialog / bottom-sheet window.
 *
 * A dialog is its own window, so `Modifier.blur` cannot reach the page under it — the system has to
 * do it through the window flags. Android 12+ only, and only when blur is on; without it the dialog
 * keeps the default dim, which is still readable.
 */
@Composable
fun DialogBlurBehind(radius: Dp = 24.dp) {
    if (!LocalFrostEnabled.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val px = with(LocalDensity.current) { radius.toPx() }.toInt()
    LaunchedEffect(view, px) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        blurWindowBehind(window, px)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun blurWindowBehind(window: Window, radius: Int) {
    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    window.setBackgroundBlurRadius(radius)
}

/**
 * A user message bubble: brand-tinted glass rather than a flat block. Kept low-alpha so the text
 * still owns the contrast, with the edge light doing the separating work.
 */
@Composable
fun Modifier.glassBubble(shape: Shape = Radius.xl): Modifier {
    val c = Dsh.colors
    val top = if (c.isDark) 0.26f else 0.20f
    val bottom = if (c.isDark) 0.16f else 0.13f
    return clip(shape)
        .background(Brush.linearGradient(listOf(c.brandGradStart.copy(alpha = top), c.brandGradEnd.copy(alpha = bottom))))
        .edgeLight(shape, 0.7f)
}

/** A tint to paint over blurred content so text keeps its contrast, or [Color.Transparent] when off. */
@Composable
fun glassTint(): Color = if (LocalFrostEnabled.current) Dsh.colors.glassTint else Color.Transparent

/** Glass panel with content inside it. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.xl,
    tier: GlassTier = GlassTier.Regular,
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) = Box(modifier.frost(shape, tier, elevation), content = content)

/** Hairline that fades out at both ends — used instead of a hard rule inside glass. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier, fade: Boolean = true) {
    val line = Dsh.colors.borderL2
    Box(
        modifier.fillMaxWidth().height(Hairline).background(
            if (fade) Brush.horizontalGradient(listOf(Color.Transparent, line, Color.Transparent)) else Brush.horizontalGradient(listOf(line, line)),
        ),
    )
}

/** The brand gradient. Only for things that must be seen (send, focus, selection); never for text. */
@Composable
fun brandBrush(horizontal: Boolean = false): Brush {
    val c = Dsh.colors
    return if (horizontal) Brush.horizontalGradient(listOf(c.brandGradStart, c.brandGradEnd))
    else Brush.linearGradient(listOf(c.brandGradStart, c.brandGradEnd))
}
