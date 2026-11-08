package pt.ismai.farol.ui.screens.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import pt.ismai.farol.domain.model.NetworkResult
import pt.ismai.farol.ui.components.CheckCard
import pt.ismai.farol.ui.screens.common.BackTopBar

@Composable
fun NetworkScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: NetworkViewModel = viewModel(factory = NetworkViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    NetworkContent(state, vm::check, onBack)
}

@Composable
private fun NetworkContent(state: NetworkUiState, onCheck: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.network_title), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.network_description), style = MaterialTheme.typography.bodyMedium)
            val label = if (state is NetworkUiState.Error) R.string.network_button_retry else R.string.network_button
            Button(
                onClick = onCheck,
                enabled = state !is NetworkUiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(label)) }
            NetworkBody(state)
        }
    }
}

@Composable
private fun NetworkBody(state: NetworkUiState) {
    when (state) {
        NetworkUiState.Initial -> Unit
        NetworkUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.network_checking), Modifier.padding(start = 16.dp))
        }
        is NetworkUiState.Error -> Text(stringResource(state.message), color = MaterialTheme.colorScheme.error)
        is NetworkUiState.Success -> NetworkData(state.result)
    }
}

@Composable
private fun NetworkData(r: NetworkResult) {
    val noPorts = stringResource(R.string.network_no_ports)
    val noCves = stringResource(R.string.network_no_cves)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DataRow(stringResource(R.string.network_ip), r.publicIp)
            DataRow(stringResource(R.string.network_connection), r.connectionType)
            DataRow(stringResource(R.string.network_ports), r.ports.joinToString(", ").ifEmpty { noPorts })
            DataRow(stringResource(R.string.network_cves), r.cves.joinToString(", ").ifEmpty { noCves })
        }
    }
    CheckCard(r.exposure, onClick = null)
}

@Composable
private fun DataRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
