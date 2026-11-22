package pt.ismai.farol.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.StringRes
import pt.ismai.farol.R

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(if (ui.registering) R.string.auth_title_register else R.string.auth_title_sign_in),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.auth_subtitle), style = MaterialTheme.typography.bodyMedium)
            AuthField(
                label = R.string.auth_email,
                value = ui.email,
                onChange = viewModel::changeEmail,
                error = ui.emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )
            AuthField(
                label = R.string.auth_password,
                value = ui.password,
                onChange = viewModel::changePassword,
                error = ui.passwordError,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
                onDone = viewModel::submit,
            )
            ui.error?.let {
                Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = viewModel::submit, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                if (ui.busy) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp).height(18.dp).width(18.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(if (ui.registering) R.string.auth_action_register else R.string.auth_action_sign_in))
            }
            TextButton(onClick = viewModel::toggleMode, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (ui.registering) R.string.auth_switch_to_sign_in else R.string.auth_switch_to_register,
                    ),
                )
            }
        }
    }
}

/** Email or password field: the same OutlinedTextField with its error line, used twice on this screen. */
@Composable
private fun AuthField(
    @StringRes label: Int,
    value: String,
    onChange: (String) -> Unit,
    @StringRes error: Int?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean = false,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.auth_hide_password else R.string.auth_show_password,
                        ),
                    )
                }
            }
        } else {
            null
        },
    )
}
