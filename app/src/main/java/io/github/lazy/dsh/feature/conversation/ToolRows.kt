package io.github.lazy.dsh.feature.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.capability.ShotStore
import io.github.lazy.dsh.core.designsystem.BtnSize
import io.github.lazy.dsh.core.designsystem.BtnVariant
import io.github.lazy.dsh.core.designsystem.CodeCard
import io.github.lazy.dsh.core.designsystem.DButton
import io.github.lazy.dsh.core.designsystem.DIcon
import io.github.lazy.dsh.core.designsystem.DisclosureRow
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshMarkdown
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.ExpandBody
import io.github.lazy.dsh.core.designsystem.Hairline
import io.github.lazy.dsh.core.designsystem.InOutCard
import io.github.lazy.dsh.core.designsystem.KeyValue
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.designsystem.Screenshot
import io.github.lazy.dsh.core.designsystem.ShimmerText
import io.github.lazy.dsh.core.designsystem.Spinner
import io.github.lazy.dsh.core.designsystem.StateDot
import io.github.lazy.dsh.core.designsystem.DotKind
import io.github.lazy.dsh.core.designsystem.pressable
import io.github.lazy.dsh.core.designsystem.Chevron
import io.github.lazy.dsh.core.interactive.InteractiveArtifacts
import io.github.lazy.dsh.feature.interactive.InlineInteractive
import io.github.lazy.dsh.core.session.Block
import io.github.lazy.dsh.core.session.AUTO_CONTINUE
import io.github.lazy.dsh.core.session.NETWORK_WAIT
import io.github.lazy.dsh.core.session.USER_CONTINUE
import io.github.lazy.dsh.core.session.ToolState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val Pretty = Json { prettyPrint = true }

fun prettyJson(raw: String?): String = raw?.let {
    runCatching { Pretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), Json.parseToJsonElement(it)) }.getOrDefault(it)
} ?: ""

/** Thinking row (dsh ReasoningRow): collapsed to 思考 · first-line preview; expanded body is compact markdown indented 22. */
@Composable
fun ReasoningRow(b: Block.Reasoning, live: Boolean) {
    var open by rememberSaveable(b.key) { mutableStateOf(false) }
    val preview = remember(b.text) { b.text.lineSequence().map { it.trim().trimStart('#', '*', '-', ' ') }.firstOrNull { it.isNotBlank() } ?: "" }
    Column {
        DisclosureRow(R.drawable.dsw_think_outline, stringResource(R.string.think), preview, open, { open = !open }, live = live)
        ExpandBody(open) {
            Box(Modifier.padding(start = 22.dp, top = 4.dp, bottom = 4.dp)) { DshMarkdown(b.text, compact = true) }
        }
    }
}

/**
 * One tool call (dsh ToolRow): 24 dp disclosure row. No status icons: live rows shimmer, errors
 * turn the summary red (first error line), stopped / rejected turn it amber.
 */
@Composable
fun ToolRow(t: Block.Tool, sessionId: String, onInspect: (String) -> Unit) {
    val context = LocalContext.current
    val c = Dsh.colors
    val info = remember(t.name) { ToolPresentation.info(t.name) }
    var open by rememberSaveable(t.key) { mutableStateOf(false) }
    val live = t.state == ToolState.Running
    val summary = when (t.state) {
        ToolState.Error -> t.error ?: ToolPresentation.summary(context, t)
        ToolState.Rejected -> stringResource(R.string.tool_rejected)
        ToolState.Stopped -> stringResource(R.string.tool_stopped)
        else -> ToolPresentation.summary(context, t)
    }
    val color = when (t.state) {
        ToolState.Error -> c.error
        ToolState.Rejected, ToolState.Stopped -> c.amber
        else -> c.labelTertiary
    }
    // Directory listing off the main thread (one per tool row).
    val shots = rememberShots(t, sessionId)
    Column {
        DisclosureRow(info.icon, stringResource(info.title), summary, open, { open = !open }, live = live, summaryColor = color,
            trailing = if (shots.isNotEmpty() && !open) ({ DIcon(R.drawable.dsw_fullscreen_outline, size = 12.dp, tint = c.labelCaption) }) else null)
        ExpandBody(open) {
            Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolBody(t, info.body)
                shots.forEach { Screenshot(it) }
                InspectPill { onInspect(t.callId) }
            }
        }
        // Screenshots and generated images are the point of the call: keep them visible without expanding.
        if (!open && shots.isNotEmpty() && (info.body == BodyKind.Screen || info.body == BodyKind.Image)) ShotStrip(shots, info.body)
        // An interactive scene is reopened from the conversation, not only from the expanded row.
        if (!open && info.body == BodyKind.Interactive) {
            Box(Modifier.padding(start = 22.dp, top = 4.dp)) { InteractiveCard(t) }
        }
    }
}

/** A call's screenshots / generated images, shown whether or not the row is expanded. */
@Composable
private fun ShotStrip(shots: List<java.io.File>, body: BodyKind?, indent: Dp = 22.dp) {
    Box(Modifier.padding(start = indent, top = 4.dp, bottom = 4.dp)) {
        if (body == BodyKind.Image && shots.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shots.forEach { Screenshot(it, Modifier.width(180.dp), maxHeight = 180.dp) }
            }
        } else {
            Screenshot(shots.first(), maxHeight = 200.dp)
        }
    }
}

/** ShotStore files of one call (empty until it finishes). */
@Composable
private fun rememberShots(t: Block.Tool, sessionId: String): List<java.io.File> {
    val context = LocalContext.current
    val shots by androidx.compose.runtime.produceState(emptyList<java.io.File>(), t.callId, t.state) {
        value = if (t.state == ToolState.Done) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ShotStore.files(context, sessionId, t.callId) } else emptyList()
    }
    return shots
}

/**
 * Images a collapsed group would otherwise hide: a turn that generated a picture is collapsed into
 * one step row, so its output is drawn under the group header too.
 */
@Composable
private fun GroupInlineShots(t: Block.Tool, sessionId: String) {
    val info = remember(t.name) { ToolPresentation.info(t.name) }
    if (info.body != BodyKind.Image) return
    val shots = rememberShots(t, sessionId)
    if (shots.isNotEmpty()) ShotStrip(shots, info.body, indent = 26.dp)
}

/** Keep the usable scene visible even when its step group is collapsed. */
@Composable
private fun GroupInlineInteractive(t: Block.Tool) {
    if (ToolPresentation.info(t.name).body != BodyKind.Interactive) return
    Box(Modifier.padding(start = 26.dp, top = 4.dp)) { InteractiveCard(t) }
}

@Composable
fun InspectPill(onClick: () -> Unit) {
    val c = Dsh.colors
    Row(Modifier.heightIn(min = 28.dp).clip(Radius.sm).border(Hairline, c.borderL3, Radius.sm).pressable(onClick, Radius.sm).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DIcon(R.drawable.dsw_inspect_outline, size = 12.dp, tint = c.labelTertiary)
        Text(stringResource(R.string.inspect), style = DshType.xxs12, color = c.labelTertiary)
    }
}

private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

/** A saved scene rendered in the conversation, with an optional full-screen viewer. */
@Composable
private fun InteractiveCard(t: Block.Tool) {
    val context = LocalContext.current
    val c = Dsh.colors
    val result = remember(t.result) {
        runCatching { Json.parseToJsonElement(t.result.orEmpty()) as? JsonObject }.getOrNull()
    }
    val id = result?.str("id")
    val title = result?.str("title")
    if (id.isNullOrBlank()) {
        if (!t.result.isNullOrBlank()) CodeCard(t.result.orEmpty(), label = stringResource(R.string.tool_interactive))
        return
    }
    Column {
    Row(
        Modifier.fillMaxWidth().clip(Radius.lg).background(c.bgLayer2).border(Hairline, c.borderL1, Radius.lg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DIcon(R.drawable.dsw_sparkle, size = 20.dp, tint = c.business)
        Column(Modifier.weight(1f)) {
            Text(title ?: stringResource(R.string.interactive_default_title), style = DshType.s14m, color = c.labelPrimary, maxLines = 1)
            Text(stringResource(R.string.tool_interactive), style = DshType.xxs12, color = c.labelTertiary, maxLines = 1)
        }
        DButton(
            stringResource(R.string.interactive_open), {
                runCatching { context.startActivity(InteractiveArtifacts.intent(context, id)) }
                    .onFailure {
                        android.widget.Toast.makeText(context, R.string.interactive_open_failed, android.widget.Toast.LENGTH_SHORT).show()
                    }
            },
            size = BtnSize.Sm, variant = BtnVariant.Outline, icon = R.drawable.dsw_fullscreen_outline,
        )
    }
    InlineInteractive(id, t.result, Modifier.clip(Radius.lg))
    }
}

@Composable
private fun ToolBody(t: Block.Tool, kind: BodyKind) {
    val c = Dsh.colors
    val out = t.result.orEmpty()
    if (t.state == ToolState.Rejected) {
        Text(stringResource(R.string.tool_rejected_body), style = DshType.xs13, color = c.amber)
        return
    }
    when (kind) {
        BodyKind.Terminal -> CodeCard(out.ifBlank { "…" }, label = t.args?.str("cwd") ?: if (t.name == "adb_shell") "adb shell" else "sh", header = "$ " + (t.args?.str("command") ?: ""), maxHeight = 224.dp)
        BodyKind.Read -> CodeCard(numbered(out, 8), label = t.args?.str("path") ?: t.args?.str("file_path"))
        BodyKind.Search, BodyKind.Web, BodyKind.Text -> if (out.isNotBlank()) CodeCard(out, label = stringResource(ToolPresentation.info(t.name).title))
        BodyKind.Todo -> TodoList((t.args?.get("todos") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let { o -> (o.str("content") ?: "") to (o.str("status") ?: "pending") } } ?: emptyList())
        BodyKind.Screen, BodyKind.Image -> if (out.isNotBlank()) Text(out.take(400), style = DshType.xs13, color = c.labelTertiary)
        BodyKind.Interactive -> InteractiveCard(t)
        BodyKind.Gesture -> {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(c.codeBlock).border(Hairline, c.borderL1, Radius.lg).padding(horizontal = 14.dp, vertical = 8.dp)) {
                t.args?.forEach { (k, v) -> KeyValue(k, (v as? JsonPrimitive)?.contentOrNull ?: v.toString(), mono = true) }
                if (out.isNotBlank()) KeyValue(stringResource(R.string.col_output), out.take(600))
            }
        }
        BodyKind.Question -> InOutCard(listOf(stringResource(R.string.col_in_short) to prettyJson(t.arguments), stringResource(R.string.col_out_short) to out))
        BodyKind.InOut -> InOutCard(listOf(stringResource(R.string.col_in_short) to prettyJson(t.arguments), stringResource(R.string.col_out_short) to out))
    }
    if (t.state == ToolState.Error && kind != BodyKind.InOut && out.isBlank()) Text(t.error ?: "", style = DshType.xs13, color = c.error)
}

private fun numbered(text: String, window: Int): String {
    val lines = text.lines()
    val shown = lines.take(window)
    val pad = shown.size.toString().length
    return shown.mapIndexed { i, l -> "${(i + 1).toString().padStart(pad)}  $l" }.joinToString("\n") + if (lines.size > window) "\n… +${lines.size - window}" else ""
}

/** Checklist used by the todo_write row and the todo dock. */
@Composable
fun TodoList(items: List<Pair<String, String>>) {
    val c = Dsh.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { (content, status) ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    when (status) {
                        "completed" -> StateDot(DotKind.Done)
                        "in_progress" -> Spinner(12.dp, c.labelSecondary)
                        else -> StateDot(DotKind.Idle)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(content, style = DshType.xs13, color = if (status == "completed") c.labelTertiary else c.labelSecondary)
            }
        }
    }
}

/**
 * Consecutive thinking + tool steps (dsh ChatGroupSeat). Two or more tool calls collapse under one
 * title ("已读取屏幕并已点击 3 次"); the group stays open while live.
 */
@Composable
fun StepGroup(key: String, blocks: List<Block>, live: Boolean, sessionId: String, onInspect: (String) -> Unit) {
    val tools = blocks.filterIsInstance<Block.Tool>()
    val context = LocalContext.current
    if (tools.size < 2) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { blocks.forEach { StepBlock(it, live, sessionId, onInspect) } }
        return
    }
    val c = Dsh.colors
    val groupLive = live && (tools.any { it.state == ToolState.Running } || blocks.last() !is Block.Tool || blocks.last() == tools.last())
    var open by rememberSaveable(key) { mutableStateOf(false) }
    /** While live the group is forced open; the header can still fold it (until the group finishes). */
    var liveFolded by rememberSaveable(key) { mutableStateOf(false) }
    val isLive = groupLive && live
    val expanded = if (isLive) !liveFolded else open
    val first = remember(tools.first().name) { ToolPresentation.info(tools.first().name) }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).pressable({ if (isLive) liveFolded = !liveFolded else open = !open }, Radius.sm).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (expanded) Chevron(open = false) else DIcon(first.icon, size = 14.dp, tint = c.labelTertiary)
            }
            Spacer(Modifier.width(6.dp))
            ShimmerText(ToolPresentation.groupTitle(context, tools, groupLive && live), DshType.row13.copy(fontSize = DshType.s14.fontSize), c.labelTertiary, groupLive && live)
        }
        ExpandBody(expanded) {
            // No inner scroller: the chat list scrolls, so the newest (live) step stays in view while following.
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                blocks.forEach { StepBlock(it, live, sessionId, onInspect) }
            }
        }
        // A collapsed group must not hide a picture the user asked for.
        if (!expanded) tools.filter { it.state == ToolState.Done }.forEach { GroupInlineShots(it, sessionId) }
        // ...or the scene the user asked the assistant to draw.
        if (!expanded) tools.filter { it.state == ToolState.Done }.forEach { GroupInlineInteractive(it) }
    }
}

@Composable
private fun StepBlock(b: Block, live: Boolean, sessionId: String, onInspect: (String) -> Unit) {
    when (b) {
        is Block.Reasoning -> ReasoningRow(b, live)
        is Block.Tool -> ToolRow(b, sessionId, onInspect)
        else -> {}
    }
}

/** Compaction row (dsh CompactionItem). */
@Composable
fun CompactionRow(b: Block.Compaction) {
    var open by rememberSaveable(b.key) { mutableStateOf(false) }
    val summary = if (b.live) null else stringResource(R.string.compacted_detail, b.count, b.tokens)
    Column {
        DisclosureRow(R.drawable.dsw_api_outline, stringResource(if (b.live) R.string.compacting else R.string.compacted), summary, open,
            if (b.summary.isNotBlank()) ({ open = !open }) else null, live = b.live)
        ExpandBody(open) { Box(Modifier.padding(start = 22.dp)) { DshMarkdown(b.summary, compact = true) } }
    }
}

@Composable
fun NoticeRow(b: Block.Notice) {
    val c = Dsh.colors
    // A continuation notice whose continuation has started is history, not a warning.
    val text = when {
        b.kind == AUTO_CONTINUE -> stringResource(R.string.auto_continue_notice)
        b.kind == USER_CONTINUE -> stringResource(R.string.user_continue_notice)
        !b.resumed -> b.text
        else -> stringResource(if (b.kind == NETWORK_WAIT) R.string.network_resumed else R.string.engine_resumed)
    }
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (b.resumed) c.labelTertiary else c.warn))
        Spacer(Modifier.width(8.dp))
        Text(text, style = DshType.xs13, color = if (b.resumed) c.labelTertiary else c.warnLabel)
    }
}
