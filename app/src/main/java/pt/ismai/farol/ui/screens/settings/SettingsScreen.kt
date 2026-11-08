package pt.ismai.farol.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.ismai.farol.AppContainer
import pt.ismai.farol.R
import pt.ismai.farol.domain.model.ThemePref
import pt.ismai.farol.ui.screens.common.BackTopBar

private data class AboutSection(@StringRes val title: Int, @StringRes val text: Int)

private val ABOUT_SECTIONS = listOf(
    AboutSection(R.string.settings_about_passive_title, R.string.settings_about_passive_text),
    AboutSection(R.string.settings_about_third_parties_title, R.string.settings_about_third_parties_text),
    AboutSection(R.string.settings_about_data_title, R.string.settings_about_data_text),
    AboutSection(R.string.settings_about_limits_title, R.string.settings_about_limits_text),
)

@StringRes
private fun ThemePref.labelRes(): Int = when (this) {
    ThemePref.SYSTEM -> R.string.settings_theme_system
    ThemePref.DARK -> R.string.settings_theme_dark
    ThemePref.LIGHT -> R.string.settings_theme_light
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state is SettingsUiState.Ready) {
                val ready = state as SettingsUiState.Ready
                ThemeSection(ready.theme, vm::chooseTheme)
                TimeoutSection(ready.timeoutSeconds, vm::chooseTimeout)
            }
            AboutPrivacy()
        }
    }
}

@Composable
private fun ThemeSection(current: ThemePref, onChoose: (ThemePref) -> Unit) {
    SectionTitle(R.string.settings_theme)
    ThemePref.entries.forEach { theme ->
        RadioOption(stringResource(theme.labelRes()), theme == current) { onChoose(theme) }
    }
}

@Composable
private fun TimeoutSection(current: Int, onChoose: (Int) -> Unit) {
    SectionTitle(R.string.settings_timeout)
    TIMEOUT_OPTIONS_SECONDS.forEach { seconds ->
        RadioOption(stringResource(R.string.settings_timeout_value, seconds), seconds == current) {
            onChoose(seconds)
        }
    }
}

@Composable
private fun AboutPrivacy() {
    SectionTitle(R.string.settings_about)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ABOUT_SECTIONS.forEach { section ->
                Column {
                    Text(stringResource(section.title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(section.text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun RadioOption(label: String, selected: Boolean, onChoose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onChoose, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
    }
}
