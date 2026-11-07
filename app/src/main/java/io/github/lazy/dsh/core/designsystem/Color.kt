package io.github.lazy.dsh.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.lazy.dsh.core.data.PerformanceMode

// Ported from DeepSeek Harness ui-theme design-platform.css (docs/design-tokens.md).

/** The DSH alias palette. Feature code reads these through [Dsh.colors]; M3 roles are derived from them. */
@Immutable
data class DshColors(
    val isDark: Boolean,
    // labels
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelCaption: Color,
    val labelDimmed: Color,
    val labelOnPrimary: Color,
    val labelShimmer: Color,
    val deepDiving: Color,
    val deepDivingShimmer: Color,
    val link: Color,
    val menuIcon: Color,
    // surfaces
    val bgBase: Color,
    val bgLayer1: Color,
    val bgLayer2: Color,
    val bgLayer3: Color,
    val bgModule: Color,
    val bgSkeleton: Color,
    val sidebar: Color,
    val sidebarActive: Color,
    val bubble: Color,
    val inputMajor: Color,
    val selector: Color,
    val codeBlock: Color,
    val codeBanner: Color,
    val inlineCode: Color,
    val menuFill: Color,
    val toastBg: Color,
    // borders
    val borderL1: Color,
    val borderL2: Color,
    val borderL3: Color,
    val borderL4: Color,
    // brand / buttons / interaction
    val brandPrimary: Color,
    val buttonPrimaryDimmed: Color,
    val info: Color,
    val floating: Color,
    val hover: Color,
    val pressed: Color,
    val hoverSolid: Color,
    val hoverDanger: Color,
    val switchThumbOff: Color,
    val business: Color,
    val businessTertiary: Color,
    // status
    val error: Color,
    val errorSecondary: Color,
    val success: Color,
    val successSecondary: Color,
    val successTertiary: Color,
    val warn: Color,
    val warnSecondary: Color,
    val warnLabel: Color,
    val warnTertiary: Color,
    val amber: Color,
    val idle: Color,
    val diffAdded: Color,
    val diffDeleted: Color,
    val scrim: Color,
    // trajectory kinds (dsh context-meter segment colors)
    val kindSystem: Color,
    val kindTools: Color,
    val kindMessages: Color,
    // ---- frosted glass (glassmorphism) ----
    /** The opaque page canvas the aurora sits on (see [io.github.lazy.dsh.core.designsystem.AuroraBackdrop]). */
    val canvas: Color,
    /** Soft colour orbs behind the glass. Alpha is baked in: they are painted with a radial falloff. */
    val auroraA: Color,
    val auroraB: Color,
    val auroraC: Color,
    /** Glass fill: a top-to-bottom gradient, not a flat colour. */
    val glassFillTop: Color,
    val glassFillBottom: Color,
    /** Edge light: bright top edge, fading bottom edge. This is what reads as "glass" far more than blur. */
    val glassStrokeTop: Color,
    val glassStrokeBottom: Color,
    val glassShadow: Color,
    /** Tint painted over blurred content so text keeps its contrast. */
    val glassTint: Color,
    /** The brand gradient (send, focus ring, selection). Text never uses it. */
    val brandGradStart: Color,
    val brandGradEnd: Color,
)

val LightDsh = DshColors(
    isDark = false,
    labelPrimary = Color(0xFF0F1115), labelSecondary = Color(0xFF5B616B), labelTertiary = Color(0xFF7C828C), labelCaption = Color(0xFFA8AEB8),
    labelDimmed = Color(0xFFE1E5EE), labelOnPrimary = Color(0xFFFFFFFF), labelShimmer = Color(0x4D000000),
    deepDiving = Color(0xFF345EBA), deepDivingShimmer = Color(0xFF243D80), link = Color(0xFF4176E6), menuIcon = Color(0xFF353638),
    bgBase = Color(0xFFF6F7FB), bgLayer1 = Color(0xFFFFFFFF), bgLayer2 = Color(0xFFFFFFFF), bgLayer3 = Color(0xFFFFFFFF),
    bgModule = Color(0xFFF1F2F6), bgSkeleton = Color(0x0A000000), sidebar = Color(0xE6FFFFFF), sidebarActive = Color(0xFFE9EDF7),
    bubble = Color(0xFFE9F0FE), inputMajor = Color(0xFFFFFFFF), selector = Color(0xFFEFF1F6),
    codeBlock = Color(0xFFF7F8FC), codeBanner = Color(0xFFF7F8FC), inlineCode = Color(0xFFF3F4F9), menuFill = Color(0xF2FFFFFF),
    toastBg = Color(0xFF353638),
    borderL1 = Color(0x0A0F1115), borderL2 = Color(0x1A0F1115), borderL3 = Color(0x240F1115), borderL4 = Color(0x2E0F1115),
    brandPrimary = Color(0xFF0F1115), buttonPrimaryDimmed = Color(0xFFEBEEF2), info = Color(0xFF4176E6), floating = Color(0xFFFFFFFF),
    hover = Color(0x0F263148), pressed = Color(0x1A263148), hoverSolid = Color(0xFFF1F3F5), hoverDanger = Color(0x0DEC1313),
    switchThumbOff = Color(0xFFFFFFFF), business = Color(0xFF4176E6), businessTertiary = Color(0xFFE4EDFD),
    error = Color(0xFFEC1313), errorSecondary = Color(0xFFF25A5A), success = Color(0xFF22C55E), successSecondary = Color(0xFF4ED17E),
    successTertiary = Color(0xFFE6FAED), warn = Color(0xFFF59E0B), warnSecondary = Color(0xFFF7AD31), warnLabel = Color(0xFFDD8629),
    warnTertiary = Color(0xFFFEF5E7), amber = Color(0xFFDD8629), idle = Color(0xFFD4D4D4),
    diffAdded = Color(0x1422C55E), diffDeleted = Color(0x14EC1313), scrim = Color(0x4D000000),
    kindSystem = Color(0xFFADB2B8), kindTools = Color(0xFFA78BFA), kindMessages = Color(0xFF4D93F8),
    // Light glass is milky, not clear: on a pale canvas a see-through panel simply vanishes.
    canvas = Color(0xFFF5F7FC),
    auroraA = Color(0x304176E6), auroraB = Color(0x2A7C5CFF), auroraC = Color(0x1E38BDF8),
    glassFillTop = Color(0xB8F9FAFF), glassFillBottom = Color(0xA6F5F8FF),
    glassStrokeTop = Color(0xFFFFFFFF), glassStrokeBottom = Color(0x140F1115),
    glassShadow = Color(0x1A0B0C10), glassTint = Color(0x66FFFFFF),
    brandGradStart = Color(0xFF4176E6), brandGradEnd = Color(0xFF7C5CFF),
)

val DarkDsh = DshColors(
    isDark = true,
    labelPrimary = Color(0xFFF7F8FA), labelSecondary = Color(0xFFC6CCD6), labelTertiary = Color(0xFF98A0AD), labelCaption = Color(0xFF6E7684),
    labelDimmed = Color(0xFF3A3D46), labelOnPrimary = Color(0xFF0B0C10), labelShimmer = Color(0x73FFFFFF),
    deepDiving = Color(0xFF8DA6FF), deepDivingShimmer = Color(0xFF9C7BFF), link = Color(0xFF8FA9FF), menuIcon = Color(0xFFE4E8F0),
    bgBase = Color(0xFF0B0C10), bgLayer1 = Color(0xFF121318), bgLayer2 = Color(0xFF17181E), bgLayer3 = Color(0xFF1F2128),
    bgModule = Color(0xFF1B1D24), bgSkeleton = Color(0x14FFFFFF), sidebar = Color(0xE616171C), sidebarActive = Color(0xFF2A2D38),
    bubble = Color(0xFF232B3F), inputMajor = Color(0xFF1A1C23), selector = Color(0xFF24262E),
    codeBlock = Color(0xFF0D0E12), codeBanner = Color(0xFF14161C), inlineCode = Color(0xFF1C1E26), menuFill = Color(0xF216171C),
    toastBg = Color(0xFF3A3D46),
    borderL1 = Color(0x0FFFFFFF), borderL2 = Color(0x1FFFFFFF), borderL3 = Color(0x29FFFFFF), borderL4 = Color(0x33FFFFFF),
    brandPrimary = Color(0xFFF7F8FA), buttonPrimaryDimmed = Color(0xFF3A3D46), info = Color(0xFF8FA9FF), floating = Color(0xFF1F2128),
    hover = Color(0x14FFFFFF), pressed = Color(0x24FFFFFF), hoverSolid = Color(0xFF23262E), hoverDanger = Color(0x26F25A5A),
    switchThumbOff = Color(0xFFADB2B8), business = Color(0xFF8FA9FF), businessTertiary = Color(0xFF2E3552),
    error = Color(0xFFF25A5A), errorSecondary = Color(0xFFF25A5A), success = Color(0xFF22C55E), successSecondary = Color(0xFF4ED17E),
    successTertiary = Color(0xFF233C2C), warn = Color(0xFFF59E0B), warnSecondary = Color(0xFFF7AD31), warnLabel = Color(0xFFDD8629),
    warnTertiary = Color(0xFF27241F), amber = Color(0xFFDD8629), idle = Color(0xFF545557),
    diffAdded = Color(0x1F22C55E), diffDeleted = Color(0x1FF25A5A), scrim = Color(0x8A05060A),
    kindSystem = Color(0xFF98A0AD), kindTools = Color(0xFFA78BFA), kindMessages = Color(0xFF6E9BFF),
    canvas = Color(0xFF0B0C10),
    auroraA = Color(0x4A4176E6), auroraB = Color(0x3D7C5CFF), auroraC = Color(0x2E38BDF8),
    glassFillTop = Color(0x1FFFFFFF), glassFillBottom = Color(0x0AFFFFFF),
    glassStrokeTop = Color(0x38FFFFFF), glassStrokeBottom = Color(0x0AFFFFFF),
    glassShadow = Color(0x99000000), glassTint = Color(0x3D07080B),
    brandGradStart = Color(0xFF4176E6), brandGradEnd = Color(0xFF7C5CFF),
)

/**
 * 能耗模式's identity — the two characters on the brand sheet. 电荷 keeps the app's own electric
 * blue-violet (with the gold of the cat's lightning as its secondary accent), 叶绿 turns it into a
 * living green. Only the tokens that carry the brand change: neutrals, borders and status colours
 * stay put, so contrast, dark mode and every existing screen keep working untouched.
 */
internal fun DshColors.inBrand(brand: PerformanceMode): DshColors = when (brand) {
    PerformanceMode.Electric -> if (isDark) copy(
        business = Color(0xFF9CB0FF), businessTertiary = Color(0xFF2A3054),
        link = Color(0xFFA9BCFF), info = Color(0xFF9CB0FF), deepDiving = Color(0xFF9CB0FF),
        auroraC = Color(0x26F5B82E),
    ) else copy(
        business = Color(0xFF3D5AF1), businessTertiary = Color(0xFFE7EBFF),
        link = Color(0xFF3450D8), info = Color(0xFF3D5AF1), deepDiving = Color(0xFF3450C8),
        auroraC = Color(0x20F5B82E),
        bubble = Color(0xFFE9EFFE),
    )

    PerformanceMode.Leaf -> if (isDark) copy(
        business = Color(0xFF8FD87B), businessTertiary = Color(0xFF1E3520),
        link = Color(0xFFA3E08F), info = Color(0xFF8FD87B), deepDiving = Color(0xFFA3E08F),
        brandGradStart = Color(0xFF43A047), brandGradEnd = Color(0xFF7FBF3F),
        auroraA = Color(0x423E9E4E), auroraB = Color(0x358FD14F), auroraC = Color(0x28D9F5A8),
        canvas = Color(0xFF0A0F0B), bubble = Color(0xFF1C2E1E),
    ) else copy(
        business = Color(0xFF2F7D32), businessTertiary = Color(0xFFE6F4E2),
        link = Color(0xFF2E7D32), info = Color(0xFF2F7D32), deepDiving = Color(0xFF266B2A),
        brandGradStart = Color(0xFF2F8B3E), brandGradEnd = Color(0xFF5CA83C),
        auroraA = Color(0x2A49B45A), auroraB = Color(0x249FD16A), auroraC = Color(0x18D9F5A8),
        canvas = Color(0xFFF5FAF3), bubble = Color(0xFFE9F6E4),
        glassFillTop = Color(0xB8FAFDF7), glassFillBottom = Color(0xA6F6FCF1),
    )
}

/**
 * The mode's own element colour: the cat's lightning gold for 电荷, the spirit's living green for
 * 叶绿. Used for accents that must read as *this character*, not as the app's link colour.
 */
fun DshColors.modeAccent(mode: PerformanceMode): Color = when (mode) {
    PerformanceMode.Electric -> if (isDark) Color(0xFFFFCA42) else Color(0xFFD99B0B)
    PerformanceMode.Leaf -> if (isDark) Color(0xFF7AD664) else Color(0xFF3D9143)
}

internal fun schemeOf(c: DshColors) = if (c.isDark) darkColorScheme(    primary = c.brandPrimary, onPrimary = c.labelOnPrimary, primaryContainer = c.buttonPrimaryDimmed, onPrimaryContainer = c.labelPrimary,
    secondary = c.business, onSecondary = c.labelOnPrimary, secondaryContainer = c.businessTertiary, onSecondaryContainer = c.labelPrimary,
    tertiary = Color(0xFF5686FE), background = c.bgBase, onBackground = c.labelPrimary, surface = c.bgBase, onSurface = c.labelPrimary,
    onSurfaceVariant = c.labelSecondary, surfaceDim = c.sidebar, surfaceContainerLowest = c.bgBase, surfaceContainerLow = c.bgLayer1,
    surfaceContainer = c.bgLayer2, surfaceContainerHigh = c.bgLayer3, surfaceContainerHighest = c.bgModule, surfaceVariant = c.hoverSolid,
    inverseSurface = c.toastBg, inverseOnSurface = Color.White, inversePrimary = Color(0xFF4176E6), outline = c.borderL4, outlineVariant = c.borderL2,
    error = c.error, onError = c.labelOnPrimary, errorContainer = Color(0xFF570C0C), onErrorContainer = Color(0xFFFEE2E2), scrim = c.scrim,
    surfaceTint = Color.Transparent,
) else lightColorScheme(
    primary = c.brandPrimary, onPrimary = c.labelOnPrimary, primaryContainer = c.buttonPrimaryDimmed, onPrimaryContainer = c.labelPrimary,
    secondary = c.business, onSecondary = Color.White, secondaryContainer = c.businessTertiary, onSecondaryContainer = Color(0xFF0E3074),
    tertiary = Color(0xFF4176E6), background = c.bgBase, onBackground = c.labelPrimary, surface = c.bgBase, onSurface = c.labelPrimary,
    onSurfaceVariant = c.labelSecondary, surfaceDim = c.sidebar, surfaceContainerLowest = c.bgBase, surfaceContainerLow = c.bgLayer1,
    surfaceContainer = c.bgLayer2, surfaceContainerHigh = c.bgLayer3, surfaceContainerHighest = c.bgModule, surfaceVariant = c.hoverSolid,
    inverseSurface = c.toastBg, inverseOnSurface = Color.White, inversePrimary = Color(0xFF7AAAFF), outline = c.borderL4, outlineVariant = c.borderL2,
    error = c.error, onError = Color.White, errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D), scrim = c.scrim,
    surfaceTint = Color.Transparent,
)

val LocalDshColors = staticCompositionLocalOf { LightDsh }
