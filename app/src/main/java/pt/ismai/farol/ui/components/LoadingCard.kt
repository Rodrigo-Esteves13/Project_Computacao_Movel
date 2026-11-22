package pt.ismai.farol.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import pt.ismai.farol.R

/** Card with a spinner for a check that is still running. */
@Composable
fun LoadingCard(title: String, modifier: Modifier = Modifier) {
    val running = stringResource(R.string.check_pending)
    Card(modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = running }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
        }
    }
}
