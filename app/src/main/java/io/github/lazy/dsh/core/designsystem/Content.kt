package io.github.lazy.dsh.core.designsystem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import io.github.lazy.dsh.R
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("dsh", text))
    // Android 13+ shows its own clipboard confirmation; a Toast on top would double it.
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

/**
 * dsh MarkdownText: 14/24 body, h1 21/30 … h4 14/24, R16 code blocks on the code-block fill,
 * inline code on the inline-code fill. [compact] is the thinking style (13/20 tertiary).
 */
@Composable
fun DshMarkdown(text: String, modifier: Modifier = Modifier, compact: Boolean = false, color: Color = if (compact) Dsh.colors.labelTertiary else Dsh.colors.labelPrimary) {
    val c = Dsh.colors
    val body = (if (compact) DshType.xs13 else DshType.body).copy(color = color)
    fun h(size: Int, line: Int, w: Int) = body.copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(w))
    val code = DshType.mono12.copy(fontSize = 11.sp, lineHeight = 19.sp, color = c.labelPrimary)
    Markdown(
        text,
        colors = markdownColor(text = color, codeBackground = c.codeBlock, inlineCodeBackground = c.inlineCode, dividerColor = c.borderL2, tableBackground = c.bgModule),
        typography = markdownTypography(
            h1 = if (compact) body.copy(fontWeight = FontWeight(600)) else h(21, 30, 700),
            h2 = if (compact) body.copy(fontWeight = FontWeight(600)) else h(19, 28, 700),
            h3 = if (compact) body.copy(fontWeight = FontWeight(600)) else h(18, 26, 700),
            h4 = body.copy(fontWeight = FontWeight(600)), h5 = body.copy(fontWeight = FontWeight(600)), h6 = body.copy(fontWeight = FontWeight(600)),
            text = body, code = code, inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = body.fontSize * 0.875f),
            quote = body.copy(color = c.labelSecondary), paragraph = body, ordered = body, bullet = body, list = body,
            textLink = androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = c.link, fontWeight = FontWeight(500))),
            table = body.copy(fontSize = 13.sp, lineHeight = 22.sp),
        ),
        padding = markdownPadding(block = if (compact) 2.dp else 6.dp, codeBlock = androidx.compose.foundation.layout.PaddingValues(14.dp, 10.dp)),
        components = remember { markdownComponents(table = { m -> DshTable(m) }) },
        modifier = modifier,
    )
}

/**
 * Tables the library's own component cannot draw: it renders the first two columns only, which
 * silently dropped file names (a 3-column `# | Size | File` table showed no `File` at all).
 * Wide tables scroll sideways instead of squeezing (or losing) columns.
 */
@Composable
private fun DshTable(model: MarkdownComponentModel) {
    val c = Dsh.colors
    val rows = remember(model.content, model.node) {
        tableRows(model.content.substring(model.node.startOffset, model.node.endOffset))
    }
    if (rows.isEmpty()) return
    val columns = rows.maxOf { it.size }
    if (columns == 0) return
    val style = model.typography.table.copy(color = c.labelPrimary, fontSize = 13.sp, lineHeight = 20.sp)
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Content-aware column widths (a `#` column stays narrow, a file-name column gets the
        // room). A table that still does not fit is squeezed proportionally, and only a table
        // below its minimum cell width scrolls sideways — columns are never dropped.
        val desired = (0 until columns).map { j ->
            val longest = rows.maxOf { r -> (r.getOrNull(j) ?: "").length }
            (22 + longest * 8.0f).dp.coerceIn(56.dp, 200.dp)
        }
        val total = desired.fold(0.dp) { acc, w -> acc + w }
        val scale = if (total > maxWidth) maxWidth.value / total.value else 1f
        val widths = desired.map { w -> maxOf(56.dp, (w.value * scale).dp) }
        Column(Modifier.fillMaxWidth().clip(Radius.md).background(c.bgModule)) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderL2))
                Row(Modifier.horizontalScroll(scroll)) {
                    for (j in 0 until columns) {
                        Box(Modifier.width(widths[j]).padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(cellText(row.getOrNull(j).orEmpty(), i == 0, style, c), style = style)
                        }
                    }
                }
            }
        }
    }
}

/** The rows of a GFM table (header first); the `|---|` separator row is not a row. */
private fun tableRows(markdown: String): List<List<String>> {
    val lines = markdown.lineSequence().map { it.trim() }.filter { it.contains('|') }.toList()
    val parsed = lines.map { line ->
        line.trim('|').replace("\\|", "\u0000").split('|').map { it.replace("\u0000", "|").trim() }
    }
    return parsed.filterNot { row -> row.all { cell -> cell.isEmpty() || cell.all { it == '-' || it == ':' || it.isWhitespace() } } }
}

/** A cell's inline markdown: `**bold**` and `` `code` `` (tables are mostly names and numbers). */
private fun cellText(raw: String, header: Boolean, style: TextStyle, c: DshColors): AnnotatedString = buildAnnotatedString {
    val code = SpanStyle(fontFamily = FontFamily.Monospace, background = c.inlineCode, fontSize = style.fontSize * 0.92f)
    val bold = SpanStyle(fontWeight = FontWeight(600))
    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith("**", i) && raw.indexOf("**", i + 2) > 0 -> {
                val end = raw.indexOf("**", i + 2)
                withStyle(bold) { append(raw.substring(i + 2, end)) }
                i = end + 2
            }
            raw.startsWith('`'.toString(), i) && raw.indexOf('`', i + 1) > 0 -> {
                val end = raw.indexOf('`', i + 1)
                withStyle(code) { append(raw.substring(i + 1, end)) }
                i = end + 1
            }
            else -> { append(raw[i]); i++ }
        }
    }
    if (header) addStyle(bold, 0, length)
}

/** dsh CodeBlock / TerminalBlock: R16 card on the code-block fill with a banner and copy button. */
@Composable
fun CodeCard(text: String, modifier: Modifier = Modifier, label: String? = null, maxHeight: Dp = 260.dp, header: String? = null) {
    val c = Dsh.colors
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().clip(Radius.lg).background(c.codeBlock).border(Hairline, c.borderL1, Radius.lg)) {
        Row(Modifier.fillMaxWidth().background(c.codeBanner).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label ?: "", Modifier.weight(1f), style = DshType.mono11, color = c.labelTertiary, maxLines = 1)
            IconBtn(R.drawable.dsw_copy_outline, { copyText(context, text) }, size = 32.dp, iconSize = 14.dp, tint = c.labelTertiary, contentDescription = stringResource(R.string.copy))
        }
        if (header != null) Text(header, Modifier.padding(horizontal = 14.dp, vertical = 2.dp), style = DshType.mono11, color = c.labelPrimary)
        SelectionContainer {
            Text(text, Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp), style = DshType.mono11, color = c.labelSecondary)
        }
    }
}

/** dsh IN/OUT fallback card: label column + monospace text, hairline divider between sections. */
@Composable
fun InOutCard(sections: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = Dsh.colors
    Column(modifier.fillMaxWidth().clip(Radius.lg).background(c.codeBlock).border(Hairline, c.borderL1, Radius.lg)) {
        sections.forEachIndexed { i, (label, text) ->
            if (i > 0) HDivider()
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(label, Modifier.width(36.dp), style = DshType.mono11, color = c.labelCaption)
                Spacer(Modifier.width(8.dp))
                SelectionContainer {
                    Text(text.ifBlank { "—" }, Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()), style = DshType.mono11, color = c.labelSecondary)
                }
            }
        }
    }
}

/** dsh JsonTree: collapsible read-only JSON inspection. */
@Composable
fun JsonTree(element: JsonElement, modifier: Modifier = Modifier) {
    Column(modifier) { JsonNode(null, element, 0, true) }
}

@Composable
private fun JsonNode(key: String?, el: JsonElement, depth: Int, startOpen: Boolean) {
    val c = Dsh.colors
    val pad = Modifier.padding(start = (depth * 14).dp)
    val keyText = key?.let { "$it: " } ?: ""
    when (el) {
        is JsonObject, is JsonArray -> {
            var open by remember { mutableStateOf(startOpen && depth < 2) }
            val size = if (el is JsonObject) el.size else (el as JsonArray).size
            val brackets = if (el is JsonObject) "{…} $size" else "[…] $size"
            Row(pad.clickable { open = !open }.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Chevron(Modifier.size(12.dp), open = open, size = 12.dp)
                Text(keyText, style = DshType.mono12, color = c.labelSecondary)
                if (!open) Text(brackets, style = DshType.mono12, color = c.labelCaption)
            }
            if (open) {
                if (el is JsonObject) el.forEach { (k, v) -> JsonNode(k, v, depth + 1, false) }
                else (el as JsonArray).forEachIndexed { i, v -> JsonNode(i.toString(), v, depth + 1, false) }
            }
        }
        is JsonNull -> Text(keyText + "null", pad.padding(start = 12.dp), style = DshType.mono12, color = c.labelCaption)
        is JsonPrimitive -> SelectionContainer {
            Row(pad.padding(start = 12.dp)) {
                Text(keyText, style = DshType.mono12, color = c.labelSecondary)
                Text(if (el.isString) "\"${el.content.take(4000)}\"" else el.content, style = DshType.mono12,
                    color = if (el.isString) c.success else c.business)
            }
        }
    }
}

/** Inline screenshot thumbnail; tap opens the lightbox. */
@Composable
fun Screenshot(file: File, modifier: Modifier = Modifier, maxHeight: Dp = 240.dp) {
    var open by remember { mutableStateOf(false) }
    val c = Dsh.colors
    AsyncImage(file, stringResource(R.string.screenshot), modifier.heightIn(max = maxHeight).clip(Radius.lg).border(Hairline, c.borderL2, Radius.lg)
        .clickable { open = true }, contentScale = ContentScale.Fit)
    if (open) Lightbox(file) { open = false }
}

/** dsh ImageLightbox: blurred dark stage, pinch to zoom, floating frosted action pill (Save / Share / Reset zoom) & close button. */
@Composable
fun Lightbox(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val savedMsg = stringResource(R.string.sticker_saved)
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        DialogBlurBehind()
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { z, p, _ ->
            scale = (scale * z).coerceIn(1f, 6f)
            offset = if (scale <= 1.02f) Offset.Zero else offset + p
        }
        Box(Modifier.fillMaxSize().background(Color(0xE0080A10)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
            AsyncImage(
                file, null,
                Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 48.dp).transformable(state)
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit,
            )
            // Top-right frosted circular close button
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0x991C1E26))
                    .border(Hairline, Color(0x40FFFFFF), androidx.compose.foundation.shape.CircleShape),
            ) {
                IconBtn(
                    R.drawable.dsw_close_outline, onDismiss,
                    size = 40.dp, iconSize = 18.dp, tint = Color.White,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    contentDescription = stringResource(R.string.close),
                )
            }
            // Bottom frosted glass pill bar: Save to Gallery + Share + optional Reset Zoom
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xB8181B24))
                    .border(Hairline, Color(0x44FFFFFF), androidx.compose.foundation.shape.CircleShape)
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DButton(
                    stringResource(R.string.sticker_save),
                    {
                        scope.launch(Dispatchers.IO) {
                            val ok = runCatching {
                                val mime = if (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true)) "image/jpeg" else "image/png"
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "DSH_${System.currentTimeMillis()}.${file.extension.ifBlank { "png" }}")
                                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/DSH")
                                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                                    }
                                }
                                val resolver = context.contentResolver
                                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                                try {
                                val stream = resolver.openOutputStream(uri) ?: error("No image output stream")
                                stream.use { out -> file.inputStream().use { it.copyTo(out) } }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    values.clear()
                                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                                    resolver.update(uri, values, null, null)
                                }
                                true
                                } catch (e: Exception) {
                                    runCatching { resolver.delete(uri, null, null) }
                                    throw e
                                }
                            }.getOrDefault(false)
                            if (ok) withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, savedMsg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    variant = BtnVariant.Ghost,
                    size = BtnSize.Sm,
                    icon = R.drawable.dsw_download_outline,
                )
                Box(Modifier.width(1.dp).height(14.dp).background(Color(0x33FFFFFF)))
                DButton(
                    stringResource(R.string.share),
                    {
                        scope.launch(Dispatchers.IO) {
                            val uri = runCatching {
                                val target = if (file.canonicalPath.startsWith(context.cacheDir.canonicalPath)) file else {
                                    val copy = File(File(context.cacheDir, "shares").apply { mkdirs() }, file.name)
                                    file.copyTo(copy, overwrite = true)
                                    copy
                                }
                                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", target)
                            }.getOrNull() ?: return@launch
                            val mime = if (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true)) "image/jpeg" else "image/png"
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    },
                    variant = BtnVariant.Ghost,
                    size = BtnSize.Sm,
                    icon = R.drawable.dsw_share_outline,
                )
                if (scale > 1.05f) {
                    Box(Modifier.width(1.dp).height(14.dp).background(Color(0x33FFFFFF)))
                    Text(
                        "${(scale * 100).toInt()}%",
                        Modifier.clip(Radius.sm).clickable { scale = 1f; offset = Offset.Zero }.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = DshType.mono11,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(key, Modifier.width(96.dp), style = DshType.xs13, color = Dsh.colors.labelTertiary)
        SelectionContainer(Modifier.weight(1f)) { Text(value, style = if (mono) DshType.mono12 else DshType.xs13, color = Dsh.colors.labelPrimary) }
    }
}
