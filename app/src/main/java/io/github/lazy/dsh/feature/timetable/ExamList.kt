package io.github.lazy.dsh.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.designsystem.DButton
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshType
import io.github.lazy.dsh.core.designsystem.KeyValue
import io.github.lazy.dsh.core.designsystem.Radius
import io.github.lazy.dsh.core.timetable.Exam
import io.github.lazy.dsh.core.timetable.termIdFor
import java.time.LocalDate

/** Separate from the period grid: an exam's actual start/end may span several class blocks. */
@Composable
internal fun ExamList(
    exams: List<Exam>,
    term: String,
    fetchedAt: String,
    issue: String?,
    syncing: Boolean,
    onSync: () -> Unit,
) {
    val c = Dsh.colors
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.tt_exams), style = DshType.l20, color = c.labelPrimary)
                if (fetchedAt.isNotBlank()) {
                    Text("$term · ${stringResource(R.string.tt_exam_updated)} $fetchedAt", style = DshType.xxs12, color = c.labelTertiary)
                }
                if (fetchedAt.isNotBlank() && term != termIdFor(LocalDate.now())) {
                    Text(stringResource(R.string.tt_exam_old_term), style = DshType.xs13, color = c.amber)
                }
                issue?.let { Text(it, style = DshType.xs13, color = c.amber) }
                if (exams.isEmpty()) {
                    Text(
                        stringResource(if (fetchedAt.isBlank()) R.string.tt_exam_not_synced else R.string.tt_exam_empty),
                        style = DshType.s14, color = c.labelSecondary,
                    )
                }
                DButton(stringResource(R.string.tt_exam_sync), onSync, enabled = !syncing)
            }
        }
        itemsIndexed(exams) { _, exam ->
            Column(
                Modifier.fillMaxWidth().clip(Radius.lg).background(c.bgLayer2).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(exam.course, style = DshType.s14m, color = c.labelPrimary)
                Text(
                    "${exam.date.ifBlank { stringResource(R.string.tt_exam_pending) }}  ${exam.clockLabel.ifBlank { stringResource(R.string.tt_exam_pending) }}",
                    style = DshType.s14, color = c.business,
                )
                if (exam.campus.isNotBlank()) KeyValue(stringResource(R.string.tt_exam_campus), exam.campus)
                KeyValue(stringResource(R.string.tt_exam_room), exam.room.ifBlank { stringResource(R.string.tt_exam_pending) })
                KeyValue(stringResource(R.string.tt_exam_seat), exam.seat.ifBlank { stringResource(R.string.tt_exam_pending) })
                val type = listOf(exam.kind, exam.arrangement).filter { it.isNotBlank() }.joinToString(" · ")
                if (type.isNotBlank()) Text(type, style = DshType.xs13, color = c.labelSecondary)
                if (exam.note.isNotBlank()) Text(exam.note, style = DshType.xs13, color = c.labelSecondary)
            }
        }
    }
}
