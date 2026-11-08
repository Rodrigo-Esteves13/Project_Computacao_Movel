package pt.ismai.farol.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.ismai.farol.R
import pt.ismai.farol.domain.model.CheckState

/** Critical 0-49, Warning 50-79, Good 80-100. */
fun stateForScore(score: Int): CheckState = when {
    score >= 80 -> CheckState.OK
    score >= 50 -> CheckState.WARNING
    else -> CheckState.FAIL
}

/**
 * Large number + icon + label. score == null shows a dash; [analyzing] shows
 * "Analyzing" with a spinner (the score does not exist yet).
 */
@Composable
fun ScoreGauge(score: Int?, modifier: Modifier = Modifier, analyzing: Boolean = false) {
    val state = score?.let(::stateForScore) ?: CheckState.UNAVAILABLE
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            score?.toString() ?: "—",
            color = state.color(),
            style = MaterialTheme.typography.displayLarge,
        )
        if (analyzing && score == null) {
            AnalyzingLabel()
        } else {
            StateLabel(state)
        }
    }
}

@Composable
private fun AnalyzingLabel() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.score_analyzing),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun StateLabel(state: CheckState) {
    val label = when (state) {
        CheckState.OK -> R.string.score_good
        CheckState.WARNING -> R.string.score_warning
        CheckState.FAIL -> R.string.score_critical
        CheckState.UNAVAILABLE -> R.string.state_unavailable
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateIcon(state, withDescription = false)
        Text(
            stringResource(label),
            color = state.color(),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
