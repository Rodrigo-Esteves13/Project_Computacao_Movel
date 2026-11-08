package pt.ismai.farol.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.ismai.farol.R
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.ui.theme.LocalStateColors

@Composable
fun CheckState.color(): Color {
    val colors = LocalStateColors.current
    return when (this) {
        CheckState.OK -> colors.good
        CheckState.WARNING -> colors.warning
        CheckState.FAIL -> colors.critical
        CheckState.UNAVAILABLE -> colors.neutral
    }
}

fun CheckState.icon(): ImageVector = when (this) {
    CheckState.OK -> Icons.Filled.CheckCircle
    CheckState.WARNING -> Icons.Filled.Warning
    CheckState.FAIL -> Icons.Filled.Error
    CheckState.UNAVAILABLE -> Icons.AutoMirrored.Filled.HelpOutline
}

fun CheckState.labelRes(): Int = when (this) {
    CheckState.OK -> R.string.state_ok
    CheckState.WARNING -> R.string.state_warning
    CheckState.FAIL -> R.string.state_fail
    CheckState.UNAVAILABLE -> R.string.state_unavailable
}

/** [withDescription] = false when a visible label sits next to it (avoids double reading in TalkBack).
 * Single place mapping state -> icon/color/description. Reuse in cards, score and lists. */
@Composable
fun StateIcon(state: CheckState, modifier: Modifier = Modifier, withDescription: Boolean = true) {
    Icon(
        imageVector = state.icon(),
        contentDescription = if (withDescription) stringResource(state.labelRes()) else null,
        tint = state.color(),
        modifier = modifier.size(28.dp),
    )
}
