package pt.ismai.farol.ui.screens.result

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.ismai.farol.R
import pt.ismai.farol.domain.Checks
import pt.ismai.farol.domain.Lookalike
import pt.ismai.farol.domain.LookalikeKind
import pt.ismai.farol.domain.LookalikeWarning
import pt.ismai.farol.domain.ScoreCalculator
import pt.ismai.farol.domain.model.TargetType
import pt.ismai.farol.domain.model.AnalysisProgress
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.domain.model.CheckId
import pt.ismai.farol.ui.components.LoadingCard
import pt.ismai.farol.ui.components.CheckCard
import pt.ismai.farol.ui.components.ScoreGauge
import pt.ismai.farol.ui.screens.common.BackTopBar

private val SPACING = 16.dp

@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    target: String,
    onBack: () -> Unit,
    onDetail: (analysisId: Long, check: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val title = stringResource(R.string.result_share)
    Scaffold(
        topBar = {
            BackTopBar(target, onBack) {
                ResultActions(
                    favorite = favorite,
                    onToggle = viewModel::toggleFavorite,
                    onShare = { viewModel.report()?.let { share(context, it, title) } },
                )
            }
        },
    ) { padding ->
        ResultContent(state, viewModel::reanalyze, viewModel::retry, onDetail, Modifier.padding(padding))
    }
}

@Composable
private fun RowScope.ResultActions(favorite: Boolean, onToggle: () -> Unit, onShare: () -> Unit) {
    IconButton(onClick = onToggle) {
        val label = if (favorite) R.string.result_favorite_remove else R.string.result_favorite_add
        Icon(
            if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = stringResource(label),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
    IconButton(onClick = onShare) {
        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.result_share))
    }
}

@Composable
private fun ResultContent(
    state: ResultUiState,
    onReanalyze: () -> Unit,
    onRetry: () -> Unit,
    onDetail: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ResultUiState.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is ResultUiState.Error -> ErrorContent(state.message, onRetry, modifier)
        is ResultUiState.Success -> ResultList(state.progress, onReanalyze, onDetail, modifier)
    }
}

@Composable
private fun ResultList(
    progress: AnalysisProgress,
    onReanalyze: () -> Unit,
    onDetail: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lookalike = remember(progress.target, progress.type) {
        if (progress.type == TargetType.DOMAIN) Lookalike.detect(progress.target) else null
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(SPACING),
        verticalArrangement = Arrangement.spacedBy(SPACING),
    ) {
        item { ScoreGauge(progress.score, Modifier.fillMaxWidth(), analyzing = !progress.finished) }
        lookalike?.let { warning -> item { LookalikeCard(warning) } }
        if (progress.finished && progress.score != null) item { ScoreBasis(progress.completed) }
        if (progress.fromCache) item { Text(stringResource(R.string.result_cache), style = MaterialTheme.typography.bodyMedium) }
        items(progress.completed, key = { it.id }) { v ->
            CheckCard(v, onClick = progress.analysisId?.let { id -> { onDetail(id, v.id) } })
        }
        items(progress.pending, key = { it }) { LoadingCard(Checks.title(it)) }
        item { Text(stringResource(R.string.result_notice), style = MaterialTheme.typography.bodySmall) }
        item {
            OutlinedButton(onClick = onReanalyze, enabled = progress.finished, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.result_reanalyze))
            }
        }
    }
}

/** A hint, not a verdict: the name may be imitating a known one. It does not change the score. */
@Composable
private fun LookalikeCard(warning: LookalikeWarning) {
    val reason = when (warning.kind) {
        LookalikeKind.MIXED_ALPHABETS -> stringResource(R.string.lookalike_mixed, warning.shown)
        LookalikeKind.OTHER_ALPHABET -> stringResource(R.string.lookalike_other, warning.shown)
        LookalikeKind.SIMILAR_TO_BRAND -> stringResource(R.string.lookalike_brand, warning.brand.orEmpty())
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(SPACING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.lookalike_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** Says how many checks the score rests on and, when it applies, why the score was held down. */
@Composable
private fun ScoreBasis(checks: List<Check>) {
    val coverage = ScoreCalculator.coverage(checks)
    val cap = ScoreCalculator.cap(checks)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.result_basis, coverage.available, coverage.total),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (cap != null) {
            val reason = if (cap.checkId == CheckId.CERTIFICATE) R.string.result_cap_certificate else R.string.result_cap_exposure
            Text(stringResource(reason, cap.limit), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorContent(message: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(SPACING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(message), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = SPACING)) {
            Text(stringResource(R.string.result_try_again))
        }
    }
}

private fun share(context: Context, text: String, title: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, title))
}
