package io.github.lazy.dsh.feature.conversation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.data.PerformanceMode
import io.github.lazy.dsh.core.data.StickerCatalog
import io.github.lazy.dsh.core.data.StickerDef
import io.github.lazy.dsh.core.designsystem.BtnSize
import io.github.lazy.dsh.core.designsystem.BtnVariant
import io.github.lazy.dsh.core.designsystem.DButton
import io.github.lazy.dsh.core.designsystem.DSheet
import io.github.lazy.dsh.core.designsystem.DialogBlurBehind
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.GlassTier
import io.github.lazy.dsh.core.designsystem.Hairline
import io.github.lazy.dsh.core.designsystem.IconBtn
import io.github.lazy.dsh.core.designsystem.LocalPerformanceMode
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.designsystem.Segmented
import io.github.lazy.dsh.core.designsystem.Tag
import io.github.lazy.dsh.core.designsystem.Tone
import io.github.lazy.dsh.core.designsystem.frost
import io.github.lazy.dsh.core.designsystem.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 表情包 — the character stickers the assistant sends.
 *
 * The model writes a tag in its reply ([StickerCatalog.prompt] teaches it the pack); these
 * composables render that tag as the transparent artwork, and let the user look the pack up,
 * save a face to the gallery, or share it. Nothing here sends a sticker as the user: that is the
 * assistant's line, so the composer carries no picker.
 */

/** Renders the stickers an assistant reply (or a forwarded message) carries. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MessageStickers(stickers: List<StickerDef>, modifier: Modifier = Modifier, standalone: Boolean = false) {
    if (stickers.isEmpty()) return
    val c = Dsh.colors
    var preview by remember { mutableStateOf<StickerDef?>(null) }
    val size: Dp = when {
        standalone && stickers.size == 1 -> 132.dp
        stickers.size == 1 -> 104.dp
        else -> 84.dp
    }
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stickers.forEach { sticker ->
            val accent = PerformanceUi.emblemAccent(sticker.character, c.isDark)
            Box(
                Modifier.size(size)
                    .clip(Radius.xl)
                    .background(
                        Brush.radialGradient(
                            0f to accent.copy(alpha = if (c.isDark) 0.18f else 0.13f),
                            0.72f to accent.copy(alpha = if (c.isDark) 0.04f else 0.02f),
                            1f to Color.Transparent,
                        ),
                    )
                    .pressable({ preview = sticker }, Radius.xl)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(sticker.res),
                    contentDescription = sticker.tag,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
    preview?.let { StickerPreviewDialog(it) { preview = null } }
}

/** The pack as a read-only gallery (设置 → 通用 → 表情包): one set per 能耗模式, never mixed. */
@Composable
fun StickerGallerySheet(onDismiss: () -> Unit) {
    val c = Dsh.colors
    val haptics = LocalHapticFeedback.current
    // The tab the conversation would actually send from: 电荷 → 电荷猫, 叶绿 → 叶绿精灵.
    val current = LocalPerformanceMode.current
    val currentTab = if (current == PerformanceMode.Leaf) 1 else 0
    var tab by remember { mutableIntStateOf(currentTab) }
    var preview by remember { mutableStateOf<StickerDef?>(null) }
    val list = if (tab == 1) StickerCatalog.leaf else StickerCatalog.electric
    DSheet(onDismiss, maxHeightFraction = 0.8f) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sticker_title), style = DshType.base16m, color = c.labelPrimary)
                        Tag("${list.size}", Tone.Info, mono = true)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.sticker_gallery_desc), style = DshType.xxs12, color = c.labelTertiary)
                }
                IconBtn(R.drawable.dsw_close_outline, onDismiss, size = 36.dp, iconSize = 18.dp, contentDescription = stringResource(R.string.close))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                Segmented(
                    listOf(stringResource(R.string.sticker_tab_electric), stringResource(R.string.sticker_tab_leaf)),
                    tab, { tab = it },
                )
            }
            if (tab == currentTab) Tag(stringResource(R.string.sticker_active_mode), Tone.Success)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(list, key = { it.id }) { sticker ->
                StickerGridCell(
                    sticker = sticker,
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); preview = sticker },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); preview = sticker },
                )
            }
        }
    }
    preview?.let { StickerPreviewDialog(it) { preview = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerGridCell(sticker: StickerDef, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Dsh.colors
    val accent = PerformanceUi.emblemAccent(sticker.character, c.isDark)
    Column(
        modifier = Modifier
            .clip(Radius.lg)
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = if (c.isDark) 0.14f else 0.09f),
                        c.bgModule.copy(alpha = if (c.isDark) 0.55f else 0.72f),
                    ),
                ),
            )
            .border(Hairline, accent.copy(alpha = if (c.isDark) 0.28f else 0.20f), Radius.lg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(sticker.res),
                contentDescription = sticker.tag,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            sticker.mood,
            style = DshType.xxxs11m,
            color = c.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One sticker, big: the exact face the assistant would send, with save / share. */
@Composable
fun StickerPreviewDialog(sticker: StickerDef, onDismiss: () -> Unit) {
    val c = Dsh.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = PerformanceUi.emblemAccent(sticker.character, c.isDark)
    val savedMsg = stringResource(R.string.sticker_saved)
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        DialogBlurBehind()
        Column(
            Modifier.padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .frost(Radius.panel, GlassTier.Thick, elevation = 28.dp)
                .background(c.canvas.copy(alpha = 1f))
                .border(
                    0.75.dp,
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.65f), c.glassStrokeTop, accent.copy(alpha = 0.18f))),
                    Radius.panel,
                )
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Tag(
                    if (sticker.character == PerformanceMode.Electric) "⚡ 电荷猫" else "🍃 叶绿精灵",
                    if (sticker.character == PerformanceMode.Electric) Tone.Info else Tone.Success,
                )
                Spacer(Modifier.weight(1f))
                IconBtn(R.drawable.dsw_close_outline, onDismiss, size = 32.dp, iconSize = 16.dp, contentDescription = stringResource(R.string.close))
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.size(196.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.radialGradient(
                            0f to accent.copy(alpha = if (c.isDark) 0.26f else 0.18f),
                            0.68f to accent.copy(alpha = if (c.isDark) 0.08f else 0.05f),
                            1f to Color.Transparent,
                        ),
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(sticker.res),
                    contentDescription = sticker.tag,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${sticker.emoji} ${sticker.characterZh} · ${sticker.mood}",
                style = DshType.l20.copy(fontWeight = FontWeight.SemiBold),
                color = c.labelPrimary,
                textAlign = TextAlign.Center,
            )
            Text(sticker.tag, style = DshType.mono11, color = c.labelTertiary, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DButton(
                    stringResource(R.string.sticker_save),
                    {
                        scope.launch {
                            if (Stickers.saveToGallery(context, sticker)) Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    Modifier.weight(1f), variant = BtnVariant.Outline, size = BtnSize.Sm, icon = R.drawable.dsw_download_outline,
                )
                DButton(
                    stringResource(R.string.sticker_share),
                    { scope.launch { Stickers.share(context, sticker) } },
                    Modifier.weight(1f), variant = BtnVariant.Outline, size = BtnSize.Sm, icon = R.drawable.dsw_share_outline,
                )
            }
        }
    }
}

/** Disk work for the pack: nothing here touches the model, only the user's gallery and the share sheet. */
object Stickers {
    /** Saves the PNG into `Pictures/DSH` through MediaStore. */
    suspend fun saveToGallery(context: Context, sticker: StickerDef): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "DSH_${sticker.id}_${sticker.mood}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DSH")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            try {
                val stream = resolver.openOutputStream(uri) ?: error("No image output stream")
                stream.use { out -> context.resources.openRawResource(sticker.res).use { it.copyTo(out) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    check(resolver.update(uri, values, null, null) > 0)
                }
                true
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        }.getOrDefault(false)
    }

    /** Hands the PNG to another app. */
    suspend fun share(context: Context, sticker: StickerDef) {
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "stickers").apply { mkdirs() }
                val file = File(dir, "${sticker.id}.png")
                file.outputStream().use { out -> context.resources.openRawResource(sticker.res).use { it.copyTo(out) } }
                FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            }.getOrNull()
        } ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, sticker.tag).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
