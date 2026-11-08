package pt.ismai.farol.ui.screens.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.ismai.farol.AppContainer
import pt.ismai.farol.R
import pt.ismai.farol.domain.model.FavoriteInfo
import pt.ismai.farol.ui.components.SearchField
import pt.ismai.farol.ui.components.EmptyState
import pt.ismai.farol.ui.screens.common.BackTopBar
import pt.ismai.farol.ui.screens.common.CompactScore
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.ui.components.color

@Composable
fun FavoritesScreen(container: AppContainer, onBack: () -> Unit, onOpen: (target: String) -> Unit) {
    val vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val addField by vm.addField.collectAsStateWithLifecycle()
    val recheckState by vm.recheckState.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar(stringResource(R.string.favorites_title), onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            AddForm(addField, vm::changeInput, vm::add)
            RecheckBar(
                recheckState = recheckState,
                active = (state as? FavoritesUiState.Ready)?.items?.isNotEmpty() == true,
                onRecheck = vm::recheck,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                FavoritesBody(state, onOpen, vm::remove)
            }
        }
    }
}

@Composable
private fun AddForm(addField: AddFieldState, onChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(
            value = addField.input,
            onChange = onChange,
            onSearch = onAdd,
            modifier = Modifier.weight(1f),
            error = addField.error?.let { stringResource(it) },
        )
        Button(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.favorites_add))
        }
    }
}

@Composable
private fun RecheckBar(recheckState: RecheckState, active: Boolean, onRecheck: () -> Unit) {
    val running = recheckState as? RecheckState.Running
    Button(
        onClick = onRecheck,
        enabled = active && running == null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) { Text(stringResource(R.string.favorites_recheck)) }
    if (running != null) RecheckProgress(running)
    if (recheckState is RecheckState.Failed) {
        Text(stringResource(recheckState.message), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun RecheckProgress(r: RecheckState.Running) {
    val text = r.current?.let { stringResource(R.string.favorites_progress, r.done, r.total, it) }
        ?: stringResource(R.string.favorites_progress_start)
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { if (r.total > 0) r.done.toFloat() / r.total else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun FavoritesBody(state: FavoritesUiState, onOpen: (String) -> Unit, onRemove: (String) -> Unit) {
    when (state) {
        FavoritesUiState.Loading -> Unit
        is FavoritesUiState.Ready -> if (state.items.isEmpty()) {
            EmptyState(Icons.Filled.StarBorder, stringResource(R.string.favorites_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.target }) { info ->
                    FavoriteItem(info, { onOpen(info.target) }, { onRemove(info.target) })
                }
            }
        }
    }
}

@Composable
private fun FavoriteItem(info: FavoriteInfo, onOpen: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(info.target, style = MaterialTheme.typography.titleMedium)
                ChangeDetail(info)
            }
            CompactScore(info.score)
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.favorites_remove))
            }
        }
    }
}

@Composable
private fun ChangeDetail(info: FavoriteInfo) {
    val previous = info.previousScore
    val current = info.score
    if (previous != null && current != null && previous != current) {
        Text(stringResource(R.string.favorites_score_change, previous, current), style = MaterialTheme.typography.bodyMedium)
    }
    if (info.changed) {
        Text(
            stringResource(R.string.favorites_changed),
            color = CheckState.WARNING.color(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
