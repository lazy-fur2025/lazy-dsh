package io.github.lazy.dsh.feature.settings

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.lazy.dsh.core.diagnostics.DiagnosticsExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.designsystem.BtnSize
import io.github.lazy.dsh.core.designsystem.BtnVariant
import io.github.lazy.dsh.core.designsystem.DButton
import io.github.lazy.dsh.core.designsystem.DialogBlurBehind
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.designsystem.copyText

/**
 * 复制诊断信息: the whole report is shown first; only 复制 / 分享 / 导出到文件 take it anywhere (share
 * goes through the system chooser; the export adds the redacted engine log tail, [DiagnosticsExport]).
 * Nothing is sent automatically.
 */
@Composable
fun DiagnosticsSheet(text: String, onDismiss: () -> Unit) {
    val c = Dsh.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exported by remember { mutableStateOf<String?>(null) }
    io.github.lazy.dsh.core.designsystem.DSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.diag_title), Modifier.weight(1f), style = DshType.base16m, color = c.labelPrimary)
                io.github.lazy.dsh.core.designsystem.IconBtn(R.drawable.dsw_close_outline, onDismiss, size = 36.dp, contentDescription = stringResource(R.string.close))
            }
            Text(stringResource(R.string.diag_note), style = DshType.xxs12, color = c.labelTertiary)
            SelectionContainer(
                Modifier.weight(1f, fill = false).fillMaxWidth().clip(Radius.lg)
                    .background(c.codeBlock)
                    .border(io.github.lazy.dsh.core.designsystem.Hairline, c.borderL2, Radius.lg),
            ) {
                Text(text, Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp),
                    style = DshType.mono11, color = c.labelSecondary)
            }
            exported?.let { Text(it, style = DshType.xxs12, color = c.labelSecondary) }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                DButton(stringResource(R.string.diag_export), {
                    scope.launch {
                        exported = withContext(Dispatchers.IO) {
                            runCatching { context.getString(R.string.diag_exported, DiagnosticsExport.export(context, text)) }
                                .getOrElse { context.getString(R.string.diag_export_failed, it.message ?: it.javaClass.simpleName) }
                        }
                    }
                }, variant = BtnVariant.Outline, size = BtnSize.Sm, icon = R.drawable.dsw_download_outline)
                DButton(stringResource(R.string.share), {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, variant = BtnVariant.Outline, size = BtnSize.Sm, icon = R.drawable.dsw_share_outline)
                DButton(stringResource(R.string.copy), { copyText(context, text) }, size = BtnSize.Sm, icon = R.drawable.dsw_copy_outline)
            }
        }
    }
}
