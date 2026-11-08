package pt.ismai.farol.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.ismai.farol.R
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.ui.components.StateIcon
import pt.ismai.farol.ui.components.color
import pt.ismai.farol.ui.components.labelRes
import pt.ismai.farol.ui.screens.common.BackTopBar

private val SPACING = 16.dp

@Composable
fun DetailScreen(viewModel: DetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = (state as? DetailUiState.Success)?.check?.title
        ?: stringResource(R.string.detail_screen_title)
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (val e = state) {
            DetailUiState.Loading -> Box(contentModifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is DetailUiState.Error -> Box(contentModifier.fillMaxSize().padding(SPACING), Alignment.Center) {
                Text(stringResource(e.message), textAlign = TextAlign.Center)
            }
            is DetailUiState.Success -> DetailContent(e.check, contentModifier)
        }
    }
}

@Composable
private fun DetailContent(v: Check, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SPACING),
        verticalArrangement = Arrangement.spacedBy(SPACING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateIcon(v.state, withDescription = false)
            Text(
                stringResource(v.state.labelRes()),
                color = v.state.color(),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(v.summary, style = MaterialTheme.typography.titleMedium)
        if (v.weight > 0) {
            Text(stringResource(R.string.detail_points, v.points, v.weight), style = MaterialTheme.typography.bodyMedium)
        }
        Text(v.detail, style = MaterialTheme.typography.bodyLarge)
    }
}
