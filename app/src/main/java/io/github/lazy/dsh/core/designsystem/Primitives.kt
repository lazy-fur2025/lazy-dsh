package io.github.lazy.dsh.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lazy.dsh.R

// A small component kit mirroring dsh ui-primitives (Button, Switch, StateDot, DisclosureRow,
// TextShimmer, Pill, SegmentedControl, Menu, Modal, RiskConfirmation, settings cards).

fun Modifier.hairline(shape: Shape, color: Color) = border(Hairline, color, shape)

/** dsh elevation: a 0.5 px stroke as the first layer plus a very soft shadow. */
@Composable
fun Modifier.elevated(shape: Shape, stroke: Color = Dsh.colors.borderL2, elevation: Dp = 6.dp) =
    shadow(elevation, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000)).border(Hairline, stroke, shape)

@Composable
fun DIcon(@DrawableRes res: Int, modifier: Modifier = Modifier, size: Dp = 16.dp, tint: Color = LocalContentColor.current, contentDescription: String? = null) =
    Icon(painterResource(res), contentDescription, modifier.size(size), tint = tint)

/** Pressable with a bounded ripple in dsh's translucent hover tint. */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    shape: Shape = Radius.md,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    /** Share one source to drive press feedback (scale) outside the modifier. */
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val src = interactionSource ?: remember { MutableInteractionSource() }
    return clip(shape).combinedClickable(src, ripple(color = Dsh.colors.labelPrimary), enabled = enabled, role = Role.Button,
        onLongClick = onLongClick, onClick = onClick)
}

/** Compact icon button (dsh R8 icon button): a 40 dp box; Compose extends the touch target to 48 dp. */
@Composable
fun IconBtn(
    @DrawableRes res: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 40.dp,
    iconSize: Dp = 16.dp,
    tint: Color = Dsh.colors.labelSecondary,
    enabled: Boolean = true,
    shape: Shape = Radius.sm,
) {
    Box(modifier.size(size).alpha(if (enabled) 1f else 0.4f).pressable(onClick, shape, enabled), contentAlignment = Alignment.Center) {
        DIcon(res, size = iconSize, tint = tint, contentDescription = contentDescription)
    }
}

enum class BtnVariant { Primary, Outline, Ghost, Danger, Info }
enum class BtnSize(val height: Dp, val hPad: Dp, val style: TextStyle, val shape: Shape) {
    Sm(28.dp, 10.dp, DshType.xxs12m, Radius.sm), Md(36.dp, 14.dp, DshType.s14, Radius.md), Lg(44.dp, 16.dp, DshType.s14m, Radius.lg)
}

@Composable
fun DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BtnVariant = BtnVariant.Primary,
    size: BtnSize = BtnSize.Md,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val c = Dsh.colors
    val (bg, fg) = when (variant) {
        BtnVariant.Primary -> c.brandPrimary to c.labelOnPrimary
        BtnVariant.Info -> c.info to Color.White
        BtnVariant.Outline -> Color.Transparent to c.labelPrimary
        BtnVariant.Ghost -> Color.Transparent to c.labelSecondary
        BtnVariant.Danger -> c.hoverDanger to c.error
    }
    Row(
        modifier.heightIn(min = size.height).alpha(if (enabled) 1f else 0.4f)
            .clip(size.shape).background(bg)
            .then(if (variant == BtnVariant.Outline) Modifier.border(Hairline, c.borderL3, size.shape) else Modifier)
            .pressable(onClick, size.shape, enabled)
            .padding(horizontal = size.hPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) DIcon(icon, size = 14.dp, tint = fg)
        Text(text, style = size.style, color = fg, maxLines = 1)
    }
}

/** dsh Switch: 36×20 track, 16 dp thumb, 120 ms. */
@Composable
fun DSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Dsh.colors
    val x by animateFloatAsState(if (checked) 1f else 0f, tween(120), label = "switch")
    Box(
        Modifier.size(48.dp, 40.dp).alpha(if (enabled) 1f else 0.5f)
            .toggleable(checked, remember { MutableInteractionSource() }, null, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp, 20.dp).clip(CircleShape).background(if (checked) c.info else c.labelDimmed)) {
            Box(Modifier.offset(x = 2.dp + 16.dp * x, y = 2.dp).size(16.dp).shadow(1.dp, CircleShape).clip(CircleShape)
                .background(if (checked) Color.White else c.switchThumbOff))
        }
    }
}

@Composable
fun DCheckbox(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Dsh.colors
    // onChange = null: the whole row is the toggle (it carries the semantics), the box is decoration.
    Box(modifier.size(16.dp).clip(Radius.xs).background(if (checked) c.brandPrimary else Color.Transparent)
        .border(if (checked) 0.dp else 1.dp, c.borderL4, Radius.xs)
        .then(if (onChange != null) Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChange) else Modifier),
        contentAlignment = Alignment.Center) {
        if (checked) DIcon(R.drawable.dsw_check_outline, size = 12.dp, tint = c.labelOnPrimary)
    }
}

enum class DotKind { Done, Warn, Error, Idle, Info }

/** dsh StateDot: solid status dots plus the ongoing spinner. */
@Composable
fun StateDot(kind: DotKind, size: Dp = 6.dp) {
    val c = Dsh.colors
    Box(Modifier.size(size).clip(CircleShape).background(when (kind) {
        DotKind.Done -> c.success; DotKind.Warn -> c.warn; DotKind.Error -> c.error; DotKind.Idle -> c.idle; DotKind.Info -> c.business
    }))
}

/** Rotating arc, 1.5 s per turn (StateDot ongoing). */
@Composable
fun Spinner(size: Dp = 14.dp, color: Color = Dsh.colors.labelTertiary, stroke: Dp = 1.5.dp) {
    val reduced = LocalReducedMotion.current
    val angle = if (reduced) 0f else rememberInfiniteTransition(label = "spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "a").value
    Canvas(Modifier.size(size).rotate(angle)) {
        val w = stroke.toPx()
        drawCircle(color.copy(alpha = 0.25f), style = Stroke(w), radius = this.size.minDimension / 2 - w)
        drawArc(color, -90f, 100f, false, style = Stroke(w, cap = StrokeCap.Round),
            topLeft = Offset(w, w), size = androidx.compose.ui.geometry.Size(this.size.width - 2 * w, this.size.height - 2 * w))
    }
}

/**
 * dsh TextShimmer: a highlight band sweeps across "in progress" text during the first two thirds
 * of a 1.5 s loop. Implemented as an animated gradient brush on the text itself.
 */
@Composable
fun ShimmerText(
    text: String,
    style: TextStyle,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    highlight: Color = Dsh.colors.labelShimmer,
    maxLines: Int = 1,
) {
    if (!active || LocalReducedMotion.current) {
        Text(text, modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart, StartOffset(300)), label = "s")
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat().coerceAtMost(1200f).coerceAtLeast(200f)
        val hl = if (highlight.alpha < 1f) lerpOver(color, highlight) else highlight
        val brush = Brush.linearGradient(
            0f to color, 0.4f to hl, 0.6f to hl, 1f to color,
            start = Offset(w * t - w * 0.5f, 0f), end = Offset(w * t + w * 0.5f, w * 0.27f),
        )
        Text(text, style = style.copy(brush = brush), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

private fun lerpOver(base: Color, over: Color): Color {
    val a = over.alpha
    return Color(base.red * (1 - a) + over.red * a, base.green * (1 - a) + over.green * a, base.blue * (1 - a) + over.blue * a, 1f)
}

/**
 * dsh DisclosureRow: a compact 24 dp row [16 box · 14 icon] · title · dot · summary, tertiary
 * text; the icon cross-fades to a chevron while open.
 */
@Composable
fun DisclosureRow(
    @DrawableRes icon: Int,
    title: String,
    summary: String?,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    summaryColor: Color = Dsh.colors.labelTertiary,
    titleColor: Color = Dsh.colors.labelTertiary,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Dsh.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 32.dp)
            .then(if (onToggle != null) Modifier.pressable(onToggle, Radius.sm) else Modifier)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Crossfade(expanded, animationSpec = tween(100), label = "icon") { open ->
                if (open) DIcon(R.drawable.dsw_chevron_down_outline, size = 14.dp, tint = c.labelTertiary)
                else DIcon(icon, size = 14.dp, tint = c.labelTertiary)
            }
        }
        Spacer(Modifier.width(6.dp))
        ShimmerText(title, DshType.row13, titleColor, live && summary.isNullOrEmpty(), Modifier.widthIn(max = 220.dp))
        if (!summary.isNullOrEmpty()) {
            Box(Modifier.padding(horizontal = 6.dp).size(2.dp).clip(CircleShape).background(c.labelCaption))
            ShimmerText(summary, DshType.row13, summaryColor, live, Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun ExpandBody(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(160)) + androidx.compose.animation.expandVertically(tween(160)),
        exit = fadeOut(tween(100)) + androidx.compose.animation.shrinkVertically(tween(160))) {
        Column(content = content)
    }
}

enum class Tone { Neutral, Info, Success, Warn, Error }

/** Read-only capsule (dsh Tag / Pill). [icon] is a leading mark, e.g. the mode's seal. */
@Composable
fun Tag(
    text: String,
    tone: Tone = Tone.Neutral,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val c = Dsh.colors
    val (bg, fg) = when (tone) {
        Tone.Neutral -> c.selector to c.labelSecondary
        Tone.Info -> c.businessTertiary to c.business
        Tone.Success -> c.successTertiary to c.success
        Tone.Warn -> c.warnTertiary to c.warnLabel
        Tone.Error -> c.hoverDanger to c.error
    }
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(4.dp)) }
        Text(text,
            style = if (mono) DshType.mono11.copy(lineHeight = DshType.xxs12.lineHeight) else DshType.xxxs11m.copy(lineHeight = DshType.xxs12.lineHeight),
            color = fg, maxLines = 1)
    }
}

/** Selectable capsule for filters (dsh Pill). */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Dsh.colors
    val d = if (LocalReducedMotion.current) 0 else 150
    val bg by androidx.compose.animation.animateColorAsState(if (selected) c.buttonPrimaryDimmed else Color.Transparent, tween(d), label = "pillBg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) c.labelPrimary else c.labelSecondary, tween(d), label = "pillFg")
    Text(text, Modifier.clip(CircleShape).background(bg)
        .border(Hairline, if (selected) Color.Transparent else c.borderL2, CircleShape)
        .pressable(onClick, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
        style = DshType.xs13m, color = fg)
}

/** dsh SegmentedControl: R12 track, 4 dp inset, R8 sliding segment. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = Dsh.colors
    Row(modifier.clip(Radius.md).background(c.selector).padding(if (compact) 2.dp else 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val d = if (LocalReducedMotion.current) 0 else 160
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by androidx.compose.animation.animateColorAsState(
                if (on) (if (c.isDark) c.bgLayer3 else Color.White) else Color.Transparent, tween(d), label = "segBg",
            )
            val fg by androidx.compose.animation.animateColorAsState(if (on) c.labelPrimary else c.labelTertiary, tween(d), label = "segFg")
            Box(
                (if (compact) Modifier else Modifier.weight(1f)).clip(Radius.sm)
                    .then(if (on) Modifier.shadow(1.dp, Radius.sm) else Modifier).background(bg)
                    .selectable(on, role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = if (compact) 12.dp else 10.dp, vertical = if (compact) 3.dp else 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = DshType.xs13m, color = fg, maxLines = 1) }
        }
    }
}

// ---- menus ------------------------------------------------------------------------------------

/** dsh Menu: R16 glass surface, 6 dp padding, luminous top highlight, soft floating shadow. */
@Composable
fun DMenu(
    expanded: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, offset: DpOffset = DpOffset(0.dp, 6.dp),
    /** false keeps focus (and the keyboard) on the text field underneath, e.g. the "/" command menu. */
    focusable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Dsh.colors
    DropdownMenu(
        expanded, onDismiss, modifier.widthIn(min = 212.dp, max = 340.dp).padding(horizontal = 6.dp), offset = offset,
        properties = androidx.compose.ui.window.PopupProperties(focusable = focusable),
        shape = Radius.lg, containerColor = if (c.isDark) Color(0xF2252730) else Color(0xFAFFFFFF),
        tonalElevation = 0.dp, shadowElevation = 18.dp,
        border = androidx.compose.foundation.BorderStroke(
            Hairline,
            Brush.verticalGradient(
                listOf(
                    if (c.isDark) c.glassStrokeTop else Color.White,
                    if (c.isDark) c.borderL2 else c.borderL3,
                ),
            ),
        ),
        content = content,
    )
}

@Composable
fun MenuItem(
    text: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    description: String? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Dsh.colors
    val fg = when {
        danger -> c.error
        selected -> c.business
        else -> c.labelPrimary
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).alpha(if (enabled) 1f else 0.4f)
            .clip(Radius.md)
            .then(if (selected) Modifier.background(c.businessTertiary.copy(alpha = if (c.isDark) 0.55f else 0.7f)) else Modifier)
            .pressable(onClick, Radius.md, enabled)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(24.dp).clip(Radius.sm).background(
                    when {
                        danger -> c.hoverDanger
                        selected -> c.businessTertiary
                        else -> c.selector.copy(alpha = 0.65f)
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                DIcon(icon, size = 14.dp, tint = when {
                    danger -> c.error
                    selected -> c.business
                    else -> c.menuIcon
                })
            }
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = if (selected) DshType.xs13m else DshType.xs13, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description != null) Text(description, style = DshType.xxs12, color = c.labelCaption, maxLines = 2)
        }
        trailing?.invoke()
        if (selected) DIcon(R.drawable.dsw_check_outline, size = 14.dp, tint = c.business)
    }
}

@Composable
fun MenuSectionTitle(text: String) =
    Text(text, Modifier.padding(start = 10.dp, top = 8.dp, bottom = 3.dp), style = DshType.xxxs11m, color = Dsh.colors.labelTertiary)

@Composable
fun MenuDivider() = GlassDivider(Modifier.padding(vertical = 5.dp, horizontal = 8.dp))

// ---- dialogs & bottom sheets ------------------------------------------------------------------

/** dsh Modal: frosted glass panel with subtle entrance spring, luminous edge rim, and soft aura header. */
@Composable
fun DDialog(
    onDismiss: () -> Unit,
    title: String,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Dsh.colors
    val reduced = LocalReducedMotion.current
    var entered by remember { mutableStateOf(reduced) }
    val scale by animateFloatAsState(
        if (entered) 1f else 0.94f,
        if (reduced) tween(0) else androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "dialogScale",
    )
    val alpha by animateFloatAsState(
        if (entered) 1f else 0f,
        tween(if (reduced) 0 else 180, easing = Motion.enter),
        label = "dialogAlpha",
    )
    androidx.compose.runtime.LaunchedEffect(Unit) { entered = true }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        // The page under the dialog stays sharp otherwise; blur it so the dialog reads.
        DialogBlurBehind()
        Column(
            Modifier.padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .frost(Radius.panel, GlassTier.Thick, elevation = 28.dp)
                // Readability first: without a canvas scrim the page's text shows through the panel.
                .background(
                    Brush.verticalGradient(
                        listOf(
                            c.brandGradStart.copy(alpha = if (c.isDark) 0.08f else 0.05f),
                            Color.Transparent,
                        ),
                    ),
                )
                .background(c.canvas.copy(alpha = 1f))
                .border(
                    0.75.dp,
                    Brush.verticalGradient(
                        listOf(
                            c.brandGradStart.copy(alpha = if (c.isDark) 0.45f else 0.32f),
                            c.glassStrokeTop,
                            c.glassStrokeBottom,
                        ),
                    ),
                    Radius.panel,
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp, 18.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(c.brandGradStart, c.brandGradEnd))))
                Spacer(Modifier.width(10.dp))
                Text(title, Modifier.weight(1f), style = DshType.l20, color = c.labelPrimary)
            }
            GlassDivider()
            CompositionLocalProvider(LocalContentColor provides c.labelSecondary) { content() }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = buttons)
        }
    }
}

/** dsh RiskConfirmation: the confirm button stays disabled until the risk checkbox is ticked. */
@Composable
fun RiskConfirmDialog(title: String, body: String, check: String, confirm: String, cancel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Dsh.colors
    var ok by remember { mutableStateOf(false) }
    // Taps already in flight when the dialog pops up (e.g. a double tap on the menu) must not tick or confirm it.
    val shownAt = remember { android.os.SystemClock.uptimeMillis() }
    val settled = { android.os.SystemClock.uptimeMillis() - shownAt >= 500 }
    DDialog(onDismiss, title, buttons = {
        DButton(cancel, onDismiss, variant = BtnVariant.Outline)
        DButton(confirm, { if (ok && settled()) onConfirm() }, variant = if (ok) BtnVariant.Danger else BtnVariant.Primary, enabled = ok)
    }) {
        Row(
            Modifier.fillMaxWidth().clip(Radius.md).background(c.warnTertiary).border(Hairline, c.warnSecondary, Radius.md).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(c.warn))
            Text(body, style = DshType.s14, color = c.labelPrimary)
        }
        Row(
            Modifier.fillMaxWidth().clip(Radius.md).background(if (ok) c.hover else c.bgModule.copy(alpha = 0.6f))
                .border(Hairline, if (ok) c.brandPrimary.copy(alpha = 0.5f) else c.borderL2, Radius.md)
                .toggleable(ok, role = Role.Checkbox) { if (settled()) ok = it }
                .heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DCheckbox(ok, null)
            Text(check, style = DshType.s14, color = c.labelPrimary)
        }
    }
}

/** Shared frosted glass bottom sheet (`DSheet`) with blurred backdrop, top pill handle, and luminous top border. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.86f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Dsh.colors
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shape = Radius.panel.copy(
        bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
        bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Color.Transparent,
        scrimColor = Color(0x78000000),
        tonalElevation = 0.dp,
        shape = shape,
        dragHandle = null,
    ) {
        DialogBlurBehind()
        val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * maxHeightFraction).dp
        Column(
            modifier.fillMaxWidth()
                .heightIn(max = maxH)
                .frost(shape, GlassTier.Thick, elevation = 24.dp)
                .background(c.canvas.copy(alpha = 1f))
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(c.borderL3))
            }
            content()
        }
    }
}

// ---- inputs -----------------------------------------------------------------------------------

@Composable
fun DInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    minHeight: Dp = 40.dp,
) {
    val c = Dsh.colors
    BasicTextField(
        value, onChange, modifier.fillMaxWidth(),
        singleLine = singleLine,
        textStyle = DshType.s14.copy(color = c.labelPrimary),
        cursorBrush = SolidColor(c.business),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = minHeight).clip(Radius.md).background(c.bgModule).border(Hairline, c.borderL2, Radius.md)
                .padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = DshType.s14, color = c.labelCaption)
                inner()
            }
        },
    )
}

// ---- settings cards -----------------------------------------------------------------------------

/** dsh settings card: glass panel with a soft edge light; rows separated by hairlines. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Dsh.colors
    // Readability first: a light canvas scrim under the fill keeps the aurora grid from running
    // through row text, while the edge light and shadow still read as a pane of glass.
    Column(
        modifier.fillMaxWidth().frost(Radius.panel, GlassTier.Thick, elevation = 6.dp)
            .background(c.canvas.copy(alpha = if (c.isDark) 0.55f else 0.35f)),
        content = content,
    )
}

@Composable
fun SettingsGroupTitle(text: String) =
    Text(text, Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp), style = DshType.xs13m, color = Dsh.colors.labelTertiary)

/**
 * One settings row. On narrow screens dsh stacks the label above the control; short trailing
 * controls (switches, chevrons) stay inline.
 */
@Composable
fun SettingsRow(
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    stacked: Boolean = false,
    titleColor: Color = Dsh.colors.labelPrimary,
    /** The title's face: a row that carries the brand uses the mode's wordmark. */
    titleStyle: androidx.compose.ui.text.TextStyle = DshType.s14,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Dsh.colors
    Column {
        if (divider) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(Hairline).background(c.borderL2))
        val base = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp)
        if (stacked) Column(base, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = titleStyle, color = titleColor)
                if (description != null) Text(description, style = DshType.xs13, color = c.labelTertiary)
            }
            trailing?.invoke()
        } else Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = titleStyle, color = titleColor)
                if (description != null) Text(description, style = DshType.xs13, color = c.labelTertiary)
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun HDivider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(Hairline).background(Dsh.colors.borderL2))

@Composable
fun Chevron(modifier: Modifier = Modifier, open: Boolean = false, size: Dp = 14.dp, tint: Color = Dsh.colors.labelTertiary) {
    val r by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "chev")
    DIcon(R.drawable.dsw_chevron_down_outline, modifier.rotate(r), size = size, tint = tint)
}

/** Small top-bar for secondary screens (back · title · actions). [glass] floats it on the aurora. */
@Composable
fun DTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bar = if (glass) Modifier.padding(horizontal = 8.dp).padding(top = 6.dp).height(48.dp)
        .frost(Radius.lg, GlassTier.Thin, elevation = 8.dp).padding(horizontal = 2.dp)
    else Modifier.height(56.dp).padding(horizontal = 8.dp)
    Row(modifier.fillMaxWidth().then(bar), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconBtn(R.drawable.dsw_chevron_left_outline, onBack, size = 40.dp, iconSize = 18.dp, tint = Dsh.colors.labelPrimary,
            contentDescription = androidx.compose.ui.res.stringResource(R.string.back))
        else Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f).padding(horizontal = 4.dp), style = DshType.base16m, color = Dsh.colors.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Empty-state block: an optional quiet mark (a paw, a leaf branch) above one line of text. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier, art: Int? = null) {
    if (art == null) {
        Text(text, modifier.padding(PaddingValues(24.dp)), style = DshType.s14, color = Dsh.colors.labelTertiary)
        return
    }
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 28.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painterResource(art), null,
            Modifier.size(64.dp).alpha(0.5f),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Spacer(Modifier.height(10.dp))
        Text(text, style = DshType.s14, color = Dsh.colors.labelTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

fun Modifier.circleBg(color: Color) = clip(RoundedCornerShape(50)).background(color)
