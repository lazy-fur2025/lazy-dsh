package io.github.lazy.dsh.feature.files

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.designsystem.BtnVariant
import io.github.lazy.dsh.core.designsystem.DButton
import io.github.lazy.dsh.core.designsystem.DDialog
import io.github.lazy.dsh.core.designsystem.DIcon
import io.github.lazy.dsh.core.designsystem.DMenu
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.EmptyNote
import io.github.lazy.dsh.core.designsystem.IconBtn
import io.github.lazy.dsh.core.designsystem.MenuItem
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.designsystem.SettingsCard
import io.github.lazy.dsh.core.designsystem.SettingsGroupTitle
import io.github.lazy.dsh.core.designsystem.SettingsRow
import io.github.lazy.dsh.core.office.OfficeExport
import io.github.lazy.dsh.core.office.OfficeFormat
import io.github.lazy.dsh.feature.settings.SettingsPage
import java.io.File

/**
 * 文件: every Office export, newest first. A tap opens the file in whatever editor the phone has
 * (Word, WPS, Sheets); the trailing menu shares or deletes it.
 *
 * The list is the directory itself — no database to keep in sync, and a file the user drops in with
 * a file manager shows up on the next visit.
 */
@Composable
fun FilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(OfficeExport.list(context)) }
    var menu by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }

    fun reload() { files = OfficeExport.list(context) }
    fun toast(res: Int) { Toast.makeText(context, res, Toast.LENGTH_SHORT).show() }

    // Coming back from an editor can leave a file changed (or gone): re-read on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }

    SettingsPage(stringResource(R.string.files), onBack) {
        Text(
            stringResource(R.string.files_desc),
            Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
            style = DshType.xs13, color = Dsh.colors.labelTertiary,
        )
        if (files.isEmpty()) {
            Spacer(Modifier.size(32.dp))
            EmptyNote(stringResource(R.string.files_empty), art = R.drawable.brand_branch)
            Text(
                stringResource(R.string.files_empty_hint),
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                style = DshType.xs13, color = Dsh.colors.labelCaption,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            return@SettingsPage
        }

        SettingsGroupTitle(stringResource(R.string.files_count, files.size))
        SettingsCard {
            files.forEachIndexed { index, file ->
                Box {
                    SettingsRow(
                        title = file.name,
                        description = OfficeExport.size(file.length()) + " · " + ago(file.lastModified()),
                        divider = index > 0,
                        onClick = { if (!OfficeExport.open(context, file)) toast(R.string.open_failed) },
                        leading = { FileBadge(file) },
                        trailing = {
                            IconBtn(
                                R.drawable.dsw_ellipsis_outline, { menu = file },
                                size = 36.dp, iconSize = 16.dp, tint = Dsh.colors.labelTertiary,
                                contentDescription = stringResource(R.string.more),
                            )
                        },
                    )
                    DMenu(expanded = menu == file, onDismiss = { menu = null }) {
                        MenuItem(
                            stringResource(R.string.share),
                            {
                                menu = null
                                if (!OfficeExport.share(context, file)) toast(R.string.files_share_failed)
                            },
                            icon = R.drawable.dsw_share_outline,
                        )
                        MenuItem(
                            stringResource(R.string.delete),
                            { menu = null; confirmDelete = file },
                            icon = R.drawable.dsw_trash_outline, danger = true,
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { file ->
        DDialog(
            onDismiss = { confirmDelete = null },
            title = stringResource(R.string.delete_file_title),
            buttons = {
                Spacer(Modifier.weight(1f))
                DButton(stringResource(R.string.cancel), { confirmDelete = null }, variant = BtnVariant.Ghost)
                DButton(stringResource(R.string.delete), {
                    file.delete()
                    confirmDelete = null
                    reload()
                }, variant = BtnVariant.Danger)
            },
        ) {
            Text(stringResource(R.string.delete_file_body, file.name), style = DshType.s14, color = Dsh.colors.labelSecondary)
        }
    }
}

/** A rounded square tinted per format, so the three types are told apart before reading the name. */
@Composable
private fun FileBadge(file: File) {
    val format = OfficeFormat.entries.firstOrNull { it.extension == file.extension.lowercase() }
    val icon = when (format) {
        OfficeFormat.DOCX -> R.drawable.dsw_deliver_doc
        OfficeFormat.PPTX -> R.drawable.dsw_flat_list_outline
        OfficeFormat.XLSX -> R.drawable.dsw_data_outline
        null -> R.drawable.dsw_folder_open_outline
    }
    val tint = when (format) {
        OfficeFormat.DOCX -> Dsh.colors.brandPrimary
        OfficeFormat.PPTX -> Dsh.colors.info
        OfficeFormat.XLSX -> Dsh.colors.success
        null -> Dsh.colors.labelTertiary
    }
    Box(
        Modifier.size(34.dp).clip(Radius.md).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { DIcon(icon, size = 17.dp, tint = tint) }
}

/** "just now / 5 min ago / 3 h ago / 11-04 16:20" — a file list does not need a full timestamp. */
@Composable
private fun ago(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.files_just_now)
        minutes < 60 -> stringResource(R.string.files_minutes_ago, minutes)
        minutes < 60 * 24 -> stringResource(R.string.files_hours_ago, minutes / 60)
        else -> java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))
    }
}
