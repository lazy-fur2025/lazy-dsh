package io.github.lazy.dsh.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.data.PerformanceMode

/**
 * The DSH dual-energy emblem (golden lightning + living leaf). Replaces the retired whale mark
 * across app chrome, onboarding, quick panel, and empty states.
 */
@Composable
fun Whale(modifier: Modifier = Modifier, width: Dp = 34.dp, tint: Color = Dsh.colors.labelPrimary) {
    val muted = tint == Dsh.colors.labelCaption || tint == Dsh.colors.labelTertiary
    Image(
        painter = painterResource(R.drawable.brand_emblem),
        contentDescription = null,
        modifier = modifier.size(width).alpha(if (muted) 0.72f else 1f),
        contentScale = ContentScale.Fit,
    )
}

/** Provider icon specifically for the DeepSeek API Key card in Settings. */
@Composable
fun DeepSeekLogo(modifier: Modifier = Modifier, width: Dp = 20.dp, tint: Color = Dsh.colors.labelPrimary) =
    Icon(painterResource(R.drawable.dsw_whale), null, modifier.size(width, width * 17.04f / 23.16f), tint = tint)

/**
 * Running indicator emblem: gentle float + pulse (1.6 s loop). Static when reduced motion is on.
 */
@Composable
fun SwimmingWhale(width: Dp = 16.dp, tint: Color = Dsh.colors.deepDiving) {
    if (LocalReducedMotion.current) { Whale(width = width, tint = tint); return }
    val t = rememberInfiniteTransition(label = "emblem")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val scale by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    Whale(
        Modifier.wrapContentSize().graphicsLayer {
            translationY = bob * 1.1f * density
            scaleX = scale
            scaleY = scale
        },
        width = width,
        tint = tint,
    )
}

/**
 * The DSH wordmark belongs to the mode. 电荷 is the fast one, so its letters are heavy and packed
 * tight (a bolt of type); 叶绿 is the calm one, so they turn into a light serif with air between
 * them (a leaf's spacing). Same name, two different faces — switching mode changes the lettering
 * everywhere the name is written, not only the colours.
 */
fun wordmarkStyle(mode: PerformanceMode, size: TextUnit = 18.sp): TextStyle = when (mode) {
    PerformanceMode.Electric -> DshType.l20.copy(
        fontSize = size, lineHeight = size * 1.34f,
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight(900), letterSpacing = (-0.7).sp,
    )
    PerformanceMode.Leaf -> DshType.l20.copy(
        fontSize = size, lineHeight = size * 1.34f,
        fontFamily = FontFamily.Serif, fontWeight = FontWeight(500), letterSpacing = 2.6.sp,
    )
}

/** The wordmark's ink: 电荷 writes in the app's own ink, 叶绿 grows into its living green. */
@Composable
fun wordmarkColor(mode: PerformanceMode, colors: DshColors = Dsh.colors): Color =
    if (mode == PerformanceMode.Leaf) colors.business else colors.labelPrimary

/**
 * "DSH" with the mode's own lettering. [size] is the cap size; the trailing [suffix] (the mode's
 * name) is set in the same face, one step smaller and in the elemental accent.
 */
@Composable
fun ModeWordmark(
    mode: PerformanceMode,
    modifier: Modifier = Modifier,
    size: TextUnit = 18.sp,
    suffix: String? = null,
) {
    val c = Dsh.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("DSH", style = wordmarkStyle(mode, size), color = wordmarkColor(mode, c))
        if (suffix != null) {
            Spacer(Modifier.width(if (mode == PerformanceMode.Leaf) 8.dp else 6.dp))
            Text(
                suffix,
                style = wordmarkStyle(mode, size * 0.52f).copy(letterSpacing = if (mode == PerformanceMode.Leaf) 1.8.sp else 2.2.sp),
                color = c.modeAccent(mode),
            )
        }
    }
}

/** Sidebar brand row: emblem + the mode's wordmark. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val mode = LocalPerformanceMode.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Whale(width = 26.dp)
        Spacer(Modifier.width(8.dp))
        ModeWordmark(mode, size = 18.sp, suffix = stringResource(modeLabel(mode)).uppercase())
    }
}

/** The mode's display name in the current locale (电荷 / 叶绿). */
fun modeLabel(mode: PerformanceMode): Int = when (mode) {
    PerformanceMode.Electric -> R.string.mode_electric
    PerformanceMode.Leaf -> R.string.mode_leaf
}
