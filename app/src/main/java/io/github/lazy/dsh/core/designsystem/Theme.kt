package io.github.lazy.dsh.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lazy.dsh.core.data.PerformanceMode
import io.github.lazy.dsh.core.data.ThemeMode

private fun style(size: Int, line: Int, weight: Int = 400, family: FontFamily = FontFamily.Default) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), fontFamily = family)

/** dsh `--dsw-font-*` scale plus the markdown/code tokens (docs/design-tokens.md). */
object DshType {
    val xl24 = style(24, 32, 600)
    val l20 = style(20, 28, 500)
    val base16 = style(16, 24)
    val base16m = style(16, 24, 500)
    val s14 = style(14, 22)
    val s14m = style(14, 22, 500)
    val xs13 = style(13, 20)
    val xs13m = style(13, 20, 500)
    val xxs12 = style(12, 18)
    val xxs12m = style(12, 18, 500)
    val xxxs11 = style(11, 14)
    val xxxs11m = style(11, 14, 500)
    val tiny10 = style(10, 16)
    /** Process rows (DisclosureRow): 13/24. */
    val row13 = style(13, 24)
    /** Markdown body 14/24. */
    val body = style(14, 24)
    val mono11 = style(11, 16, family = FontFamily.Monospace)
    val mono12 = style(12, 19, family = FontFamily.Monospace)
    val mono13 = style(13, 20, family = FontFamily.Monospace)
}

private val M3Typography = Typography(
    headlineMedium = style(28, 36, 600),
    headlineSmall = DshType.xl24,
    titleLarge = DshType.l20,
    titleMedium = DshType.base16m,
    titleSmall = DshType.s14m,
    bodyLarge = DshType.base16,
    bodyMedium = DshType.s14,
    bodySmall = DshType.xs13,
    labelLarge = DshType.s14m,
    labelMedium = DshType.xxs12m,
    labelSmall = DshType.xxxs11m,
)

/** dsh radius tiers (docs/ui-radius.md), tightened for the glass look: panel 24, card 20, field 26. */
object Radius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)
    val tile = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(20.dp)
    val panel = RoundedCornerShape(24.dp)
    /** The composer field: a capsule, but not a circle — 26 dp keeps it close to the panel tier. */
    val field = RoundedCornerShape(26.dp)
    val pill = RoundedCornerShape(50)
}

private val M3Shapes = Shapes(extraSmall = Radius.xs, small = Radius.sm, medium = Radius.md, large = Radius.lg, extraLarge = Radius.panel)

/** The DSH hairline: 0.5 px borders become 0.5 dp. */
val Hairline = 0.5.dp

/** True when the user turned animations off (dsh disables shimmer/whale/spinners under reduced motion). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Whether real backdrop blur (RenderEffect) is available and wanted: API 31+, not a low-RAM device,
 * and the user has not switched 模糊效果 off. Glass never depends on it for its look.
 */
val LocalFrostEnabled = staticCompositionLocalOf { false }

/** 降低透明度: glass fill and shadows get heavier, orbs fade, so text wins over decoration. */
val LocalReduceTransparency = staticCompositionLocalOf { false }

/** 能耗模式 in effect — every screen can read it (the hero's character, the switch's state). */
val LocalPerformanceMode = staticCompositionLocalOf { PerformanceMode.Default }

@Composable
fun DshTheme(
    mode: ThemeMode = ThemeMode.System,
    /** 能耗模式's identity: 电荷 (electric blue-violet) or 叶绿 (living green). */
    brand: PerformanceMode = PerformanceMode.Default,
    /** 模糊效果 (设置 → 通用): real backdrop blur on supported devices. */
    frost: Boolean = true,
    /** 降低透明度 (设置 → 通用): heavier glass, quieter background. */
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Dark -> true; ThemeMode.Light -> false }
    val base = (if (dark) DarkDsh else LightDsh).inBrand(brand)
    val target = if (reduceTransparency) base.quieter() else base
    val context = LocalContext.current
    val reduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val colorSpec = androidx.compose.animation.core.tween<Color>(if (reduced) 0 else Motion.glass, easing = Motion.standard)
    val business by androidx.compose.animation.animateColorAsState(target.business, colorSpec, label = "brandBusiness")
    val businessTertiary by androidx.compose.animation.animateColorAsState(target.businessTertiary, colorSpec, label = "brandBusinessTert")
    val link by androidx.compose.animation.animateColorAsState(target.link, colorSpec, label = "brandLink")
    val info by androidx.compose.animation.animateColorAsState(target.info, colorSpec, label = "brandInfo")
    val deepDiving by androidx.compose.animation.animateColorAsState(target.deepDiving, colorSpec, label = "brandDeep")
    val brandGradStart by androidx.compose.animation.animateColorAsState(target.brandGradStart, colorSpec, label = "brandGradS")
    val brandGradEnd by androidx.compose.animation.animateColorAsState(target.brandGradEnd, colorSpec, label = "brandGradE")
    val auroraA by androidx.compose.animation.animateColorAsState(target.auroraA, colorSpec, label = "auroraA")
    val auroraB by androidx.compose.animation.animateColorAsState(target.auroraB, colorSpec, label = "auroraB")
    val auroraC by androidx.compose.animation.animateColorAsState(target.auroraC, colorSpec, label = "auroraC")
    val canvas by androidx.compose.animation.animateColorAsState(target.canvas, colorSpec, label = "canvas")
    val bubble by androidx.compose.animation.animateColorAsState(target.bubble, colorSpec, label = "bubble")
    val glassFillTop by androidx.compose.animation.animateColorAsState(target.glassFillTop, colorSpec, label = "glassFillTop")
    val glassFillBottom by androidx.compose.animation.animateColorAsState(target.glassFillBottom, colorSpec, label = "glassFillBot")
    val colors = target.copy(
        business = business, businessTertiary = businessTertiary, link = link, info = info, deepDiving = deepDiving,
        brandGradStart = brandGradStart, brandGradEnd = brandGradEnd,
        auroraA = auroraA, auroraB = auroraB, auroraC = auroraC,
        canvas = canvas, bubble = bubble, glassFillTop = glassFillTop, glassFillBottom = glassFillBottom,
    )
    val frostOn = remember(frost) {
        val lowRam = (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice ?: false
        frost && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !lowRam
    }
    // System-bar icons follow the in-app theme, not the system night mode (enableEdgeToEdge's default).
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        context.findActivity()?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    CompositionLocalProvider(
        LocalDshColors provides colors,
        LocalPerformanceMode provides brand,
        LocalReducedMotion provides reduced,
        LocalFrostEnabled provides frostOn,
        LocalReduceTransparency provides reduceTransparency,
        LocalTextSelectionColors provides TextSelectionColors(colors.business, Color(0x663B82F6)),
    ) {
        MaterialTheme(colorScheme = schemeOf(colors), typography = M3Typography, shapes = M3Shapes, content = content)
    }
}

/** 降低透明度: the same palette with quieter orbs and denser glass, so nothing sits under a busy backdrop. */
private fun DshColors.quieter() = copy(
    auroraA = auroraA.copy(alpha = auroraA.alpha * 0.35f),
    auroraB = auroraB.copy(alpha = auroraB.alpha * 0.35f),
    auroraC = auroraC.copy(alpha = auroraC.alpha * 0.35f),
    glassFillTop = glassFillTop.copy(alpha = (glassFillTop.alpha + 0.35f).coerceAtMost(1f)),
    glassFillBottom = glassFillBottom.copy(alpha = (glassFillBottom.alpha + 0.35f).coerceAtMost(1f)),
    glassShadow = glassShadow.copy(alpha = glassShadow.alpha * 0.6f),
)

object Dsh {
    val colors: DshColors
        @Composable @ReadOnlyComposable get() = LocalDshColors.current
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}
