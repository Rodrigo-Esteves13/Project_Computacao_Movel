package pt.ismai.farol.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.ismai.farol.R
import pt.ismai.farol.domain.model.AnalysisSummary
import pt.ismai.farol.ui.components.SearchField
import pt.ismai.farol.ui.components.EmptyState
import pt.ismai.farol.ui.components.StateIcon
import pt.ismai.farol.ui.components.stateForScore
import pt.ismai.farol.ui.screens.common.formatDate
import pt.ismai.farol.domain.model.CheckState

private val SPACING = 16.dp
private val EMPTY_HEIGHT = 220.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAnalyze: (String) -> Unit,
    onOpenAnalysis: (analysisId: Long, target: String) -> Unit,
    onNetwork: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    Scaffold { padding ->
        HomeContent(
            ui = ui,
            latest = latest,
            onNetwork = onNetwork,
            onTextChange = viewModel::changeText,
            onAnalyze = { viewModel.validate()?.let(onAnalyze) },
            onOpenAnalysis = onOpenAnalysis,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun HomeContent(
    ui: HomeUi,
    latest: List<AnalysisSummary>,
    onNetwork: () -> Unit,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onOpenAnalysis: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(SPACING),
        verticalArrangement = Arrangement.spacedBy(SPACING),
    ) {
        item { Header() }
        item {
            SearchField(ui.text, onTextChange, onAnalyze, Modifier.fillMaxWidth(), ui.error?.let { stringResource(it) })
        }
        item {
            Button(onClick = onAnalyze, enabled = ui.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_analyze))
            }
        }
        item { NetworkCard(onNetwork) }
        item { Text(stringResource(R.string.home_latest), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
        if (latest.isEmpty()) {
            item {
                EmptyState(Icons.Filled.History, stringResource(R.string.home_no_analyses), Modifier.height(EMPTY_HEIGHT))
            }
        } else {
            items(latest, key = { it.id }) { AnalysisListItem(it) { onOpenAnalysis(it.id, it.target) } }
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text(stringResource(R.string.app_name), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The only shortcut left here: the other screens are in the bottom bar. */
@Composable
private fun NetworkCard(onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(SPACING), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.home_shortcut_network),
                Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun AnalysisListItem(analysis: AnalysisSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { StateIcon(analysis.score?.let(::stateForScore) ?: CheckState.UNAVAILABLE) },
        headlineContent = { Text(analysis.target) },
        supportingContent = { Text(formatDate(analysis.date)) },
        trailingContent = {
            analysis.score?.let { Text(stringResource(R.string.home_score_value, it)) }
        },
    )
}

