package pt.ismai.farol.ui.screens.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.ui.components.StateIcon
import pt.ismai.farol.ui.components.color
import pt.ismai.farol.ui.components.stateForScore

/** Small version of ScoreGauge for lists: number + state icon. */
@Composable
fun CompactScore(score: Int?, modifier: Modifier = Modifier) {
    val state = score?.let(::stateForScore) ?: CheckState.UNAVAILABLE
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            score?.toString() ?: "—",
            color = state.color(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(end = 8.dp),
        )
        StateIcon(state)
    }
}
