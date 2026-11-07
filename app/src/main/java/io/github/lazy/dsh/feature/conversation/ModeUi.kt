package io.github.lazy.dsh.feature.conversation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.model.ModeIcon
import io.github.lazy.dsh.core.model.SessionMode
import io.github.lazy.dsh.core.model.SessionModes
import io.github.lazy.dsh.core.model.modeDisplayName

object ModeUi {
    @DrawableRes fun icon(icon: ModeIcon) = when (icon) {
        ModeIcon.Default -> R.drawable.dsw_sparkle
        ModeIcon.Phone -> R.drawable.dsw_apps_add_outline
        ModeIcon.Code -> R.drawable.dsw_code_outline
        ModeIcon.Chat -> R.drawable.dsw_new_chat_outline
        ModeIcon.Globe -> R.drawable.dsw_globe_outline
        ModeIcon.Pen -> R.drawable.dsw_list_pen_outline
        ModeIcon.Goal -> R.drawable.dsw_goal_outline
        ModeIcon.Skill -> R.drawable.dsw_skill_outline
    }
    @DrawableRes fun icon(mode: SessionMode) = icon(ModeIcon.of(mode.icon))

    @Composable fun name(mode: SessionMode): String = modeDisplayName(LocalContext.current, mode)

    /** One-line summary under a mode's name: the built-in's description, else the start of its prompt. */
    @Composable fun summary(mode: SessionMode): String? = when (mode.id) {
        SessionModes.DEFAULT_ID -> stringResource(R.string.mode_default_desc)
        SessionModes.PHONE_ID -> stringResource(R.string.mode_phone_desc)
        SessionModes.CODE_ID -> stringResource(R.string.mode_code_desc)
        SessionModes.CHAT_ID -> stringResource(R.string.mode_chat_desc)
        else -> mode.prompt.lineSequence().firstOrNull()?.take(60)?.ifBlank { null }
    }
}
