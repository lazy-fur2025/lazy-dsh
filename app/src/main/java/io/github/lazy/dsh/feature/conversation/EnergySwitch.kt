package io.github.lazy.dsh.feature.conversation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.data.PerformanceMode
import io.github.lazy.dsh.core.designsystem.DarkDsh
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.LightDsh
import io.github.lazy.dsh.core.designsystem.LocalReducedMotion
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.designsystem.Tag
import io.github.lazy.dsh.core.designsystem.Tone
import io.github.lazy.dsh.core.designsystem.modeAccent
import io.github.lazy.dsh.core.designsystem.pressable

/** The two characters of the brand sheet, as the UI needs them. */
object PerformanceUi {
    @DrawableRes
    fun avatar(mode: PerformanceMode): Int = when (mode) {
        PerformanceMode.Electric -> R.drawable.mode_electric
        PerformanceMode.Leaf -> R.drawable.mode_leaf
    }

    /** Compact cat-head silhouette + element badge for small controls (20–28 dp). */
    @DrawableRes
    fun miniAvatar(mode: PerformanceMode): Int = when (mode) {
        PerformanceMode.Electric -> R.drawable.mode_electric_mini
        PerformanceMode.Leaf -> R.drawable.mode_leaf_mini
    }

    /** The bare element (bolt / leaf) for watermarks behind copy. */
    @DrawableRes
    fun element(mode: PerformanceMode): Int = when (mode) {
        PerformanceMode.Electric -> R.drawable.brand_bolt
        PerformanceMode.Leaf -> R.drawable.brand_leaf
    }

    /** The element in a ring — the mode's seal, for tags and 12 dp marks. */
    @DrawableRes
    fun seal(mode: PerformanceMode): Int = when (mode) {
        PerformanceMode.Electric -> R.drawable.brand_seal_electric
        PerformanceMode.Leaf -> R.drawable.brand_seal_leaf
    }

    /** The sheet's horizontal hairline: bolt in the middle for 电荷, leaves for 叶绿. */
    @DrawableRes
    fun divider(mode: PerformanceMode): Int = when (mode) {
        PerformanceMode.Electric -> R.drawable.brand_divider_electric
        PerformanceMode.Leaf -> R.drawable.brand_divider_leaf
    }

    /** Signature emblem colour that matches each sticker's own artwork. */
    fun emblemAccent(mode: PerformanceMode, isDark: Boolean): Color =
        (if (isDark) DarkDsh else LightDsh).modeAccent(mode)

    @StringRes
    fun label(mode: PerformanceMode): Int = when (mode) {
        PerformanceMode.Electric -> R.string.mode_electric
        PerformanceMode.Leaf -> R.string.mode_leaf
    }

    @StringRes
    fun desc(mode: PerformanceMode): Int = when (mode) {
        PerformanceMode.Electric -> R.string.mode_electric_desc
        PerformanceMode.Leaf -> R.string.mode_leaf_desc
    }

    /** The one-line promise of each mode, for the settings cards. */
    @StringRes
    fun tagline(mode: PerformanceMode): Int = when (mode) {
        PerformanceMode.Electric -> R.string.mode_electric_tagline
        PerformanceMode.Leaf -> R.string.mode_leaf_tagline
    }
}

/**
 * 能耗模式 switch: both character silhouettes side by side in a glass capsule, the active one
 * lit on a raised thumb and ringed in its own elemental colour (electric gold or leaf green).
 */
@Composable
fun EnergySwitch(current: PerformanceMode, onChange: (PerformanceMode) -> Unit, modifier: Modifier = Modifier) {
    val c = Dsh.colors
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val label = stringResource(R.string.mode_switch_desc, stringResource(PerformanceUi.label(current)))
    Row(
        modifier.height(30.dp).clip(Radius.pill).background(c.selector)
            .border(0.5.dp, c.borderL2, Radius.pill)
            .padding(2.dp)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        PerformanceMode.entries.forEach { m ->
            val on = m == current
            val accent = PerformanceUi.emblemAccent(m, c.isDark)
            val scale by animateFloatAsState(
                if (on) 1f else 0.85f,
                if (reduced) tween(0) else spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "modeScale",
            )
            val alpha by animateFloatAsState(
                if (on) 1f else 0.62f,
                tween(if (reduced) 0 else 200),
                label = "modeAlpha",
            )
            // The silhouettes are white with one coloured swipe: they need a tinted disc under them,
            // otherwise white sits on white in the light theme and the badge disappears.
            val thumbFill by animateColorAsState(
                if (on) accent.copy(alpha = if (c.isDark) 0.26f else 0.22f) else accent.copy(alpha = if (c.isDark) 0.14f else 0.10f),
                tween(if (reduced) 0 else 200),
                label = "modeThumb",
            )
            val ring by animateColorAsState(
                if (on) accent.copy(alpha = if (c.isDark) 0.85f else 0.7f) else Color.Transparent,
                tween(if (reduced) 0 else 200),
                label = "modeRing",
            )
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(thumbFill).border(1.dp, ring, CircleShape)
                    .pressable({ if (!on) { runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }; onChange(m) } }, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(PerformanceUi.miniAvatar(m)), null,
                    Modifier.size(21.dp).scale(scale).alpha(alpha),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/**
 * The mode as the settings page shows it: the pop-out character medallion (unclipped so ears,
 * lightning and floating leaves breathe naturally), what it does, and which one is on.
 */
@Composable
fun PerformanceCard(mode: PerformanceMode, current: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val c = Dsh.colors
    val reduced = LocalReducedMotion.current
    val accent = PerformanceUi.emblemAccent(mode, c.isDark)
    val ring by animateColorAsState(
        if (current) accent.copy(alpha = if (c.isDark) 0.78f else 0.72f) else c.borderL2,
        tween(if (reduced) 0 else 220),
        label = "perfRing",
    )
    val fill by animateColorAsState(
        if (current) c.businessTertiary.copy(alpha = if (c.isDark) 0.65f else 0.75f) else Color.Transparent,
        tween(if (reduced) 0 else 220),
        label = "perfFill",
    )
    val avatarScale by animateFloatAsState(
        if (current) 1f else 0.94f,
        if (reduced) tween(0) else spring(dampingRatio = 0.75f, stiffness = 460f),
        label = "perfAvatarScale",
    )
    val avatarAlpha by animateFloatAsState(
        if (current) 1f else 0.74f,
        tween(if (reduced) 0 else 220),
        label = "perfAvatarAlpha",
    )
    val haptic = LocalHapticFeedback.current
    val name = stringResource(PerformanceUi.label(mode))
    Box(
        modifier.fillMaxWidth().clip(Radius.lg).background(fill)
            .border(if (current) 1.5.dp else 1.dp, ring, Radius.lg)
            .pressable({ if (!current) { runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }; onSelect() } }, Radius.lg)
            .semantics { contentDescription = name },
    ) {
        // The element itself, large and quiet, behind the copy: a bolt for 电荷, a leaf for 叶绿.
        Image(
            painterResource(PerformanceUi.element(mode)), null,
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(72.dp)
                .alpha(if (current) 0.14f else 0.08f),
            contentScale = ContentScale.Fit,
        )
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(PerformanceUi.avatar(mode)), null,
                Modifier.size(60.dp).scale(avatarScale).alpha(avatarAlpha),
                contentScale = ContentScale.Fit,
            )
            Column(Modifier.padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(PerformanceUi.label(mode)), style = DshType.s14m, color = c.labelPrimary)
                    if (current) {
                        Spacer(Modifier.width(6.dp))
                        Tag(stringResource(R.string.mode_in_use), Tone.Info,
                            icon = { Image(painterResource(PerformanceUi.seal(mode)), null, Modifier.size(12.dp), contentScale = ContentScale.Fit) })
                    }
                }
                Text(stringResource(PerformanceUi.tagline(mode)), Modifier.padding(top = 2.dp), style = DshType.xxs12, color = c.labelSecondary)
                Text(stringResource(PerformanceUi.desc(mode)), Modifier.padding(top = 2.dp), style = DshType.xxs12, color = c.labelTertiary)
            }
        }
    }
}
