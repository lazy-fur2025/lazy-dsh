package io.github.lazy.dsh.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Motion tokens from docs/design-tokens.md §5, shared by navigation and component animations.
 * Every consumer must snap when [LocalReducedMotion] is true.
 *
 * Springs carry the glass look: low damping (0.9) so a panel settles with a single soft overshoot,
 * never a bounce. Chosen to converge in ~220 ms at 120 Hz and the same feel at 60 Hz.
 */
object Motion {
    /** Hover / pressed / small icon transitions. */
    const val fast = 100
    /** Default transition (fades, switches). */
    const val base = 200
    /** Page enter, drawers, panels. */
    const val slow = 280
    /** Glass panel entrances and the composer morph. */
    const val glass = 320

    val standard: Easing = FastOutSlowInEasing
    /** Enter: cubic-bezier(0.22, 1, 0.36, 1). */
    val enter: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** Exit: cubic-bezier(0.4, 0, 1, 1). */
    val exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** Drawer / sheet settle (drag release, drawer open). */
    val springDrawer: SpringSpec<Float> = spring(dampingRatio = 0.92f, stiffness = 420f)
    /** Panels, cards, expand/collapse. */
    val springPanel: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 500f)
    /** Press feedback: quick, barely overshoots. */
    val springPress: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 900f)
}
