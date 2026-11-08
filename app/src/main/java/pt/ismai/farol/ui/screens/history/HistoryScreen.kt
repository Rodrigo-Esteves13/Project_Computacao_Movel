package pt.ismai.farol.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.ismai.farol.AppContainer
import pt.ismai.farol.R
import pt.ismai.farol.domain.model.AnalysisSummary
import pt.ismai.farol.ui.components.EmptyState
import pt.ismai.farol.ui.screens.common.BackTopBar
import pt.ismai.farol.ui.screens.common.CompactScore
import pt.ismai.farol.ui.screens.common.formatDate

@Composable
fun HistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenAnalysis: (analysisId: Long, target: String) -> Unit,
) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    val hasItems = (state as? HistoryUiState.Ready)?.items?.isNotEmpty() == true

    Scaffold(
        topBar = {
            BackTopBar(stringResource(R.string.history_title), onBack) {
                if (hasItems) {
                    IconButton(onClick = { confirmDeleteAll = true }) {
                        Icon(Icons.Filled.DeleteSweep, stringResource(R.string.history_delete_all))
                    }
                }
            }
        },
    ) { padding ->
        HistoryBody(state, Modifier.padding(padding), onOpenAnalysis, vm::delete)
    }
    if (confirmDeleteAll) {
        DeleteAllDialog(
            onConfirm = {
                vm.deleteAll()
                confirmDeleteAll = false
            },
            onCancel = { confirmDeleteAll = false },
        )
    }
}

@Composable
private fun HistoryBody(
    state: HistoryUiState,
    modifier: Modifier,
    onOpen: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    when (state) {
        HistoryUiState.Loading -> Unit
        is HistoryUiState.Ready -> if (state.items.isEmpty()) {
            EmptyState(Icons.Filled.History, stringResource(R.string.history_empty), modifier)
        } else {
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    HistoryItem(item, { onOpen(item.id, item.target) }, { onDelete(item.id) })
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(item: AnalysisSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.target, style = MaterialTheme.typography.titleMedium)
                Text(formatDate(item.date), style = MaterialTheme.typography.bodyMedium)
            }
            CompactScore(item.score)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete_one))
            }
        }
    }
}

@Composable
private fun DeleteAllDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.history_dialog_title)) },
        text = { Text(stringResource(R.string.history_dialog_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.history_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.history_dialog_cancel)) }
        },
    )
}
