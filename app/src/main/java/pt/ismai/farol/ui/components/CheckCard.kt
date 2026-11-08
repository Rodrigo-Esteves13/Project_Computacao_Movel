package pt.ismai.farol.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.ismai.farol.domain.model.CheckState
import pt.ismai.farol.domain.model.Check
import pt.ismai.farol.ui.theme.FarolTheme

/** A single Composable draws any check. */
@Composable
fun CheckCard(v: Check, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            StateIcon(v.state)
            Column(Modifier.padding(start = 12.dp)) {
                Text(v.title, style = MaterialTheme.typography.titleMedium)
                Text(v.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CheckCardPreview() {
    FarolTheme {
        CheckCard(
            v = Check("certificate", "SSL certificate", CheckState.OK, "Valid, expires in 90 days", ""),
            onClick = {},
        )
    }
}
