package codes.shandon.sctotp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import codes.shandon.sctotp.R
import codes.shandon.sctotp.ui.theme.SCTOTPTheme

private const val MIN_PASSWORD_LENGTH = 8

enum class BackupMode { EXPORT, IMPORT }

// Export optionally takes a new password (typed twice); import asks for the existing one once.
// An export confirmed with no password passes null.
@Composable
fun BackupPasswordDialog(
    mode: BackupMode,
    onConfirm: (password: CharArray?) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val isExport = mode == BackupMode.EXPORT

    val tooShort = isExport && password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH
    val mismatch = isExport && confirm.isNotEmpty() && confirm != password
    val skipPassword = isExport && password.isEmpty()
    val valid = when {
        skipPassword -> true
        isExport -> password.length >= MIN_PASSWORD_LENGTH && confirm == password
        else -> password.isNotEmpty()
    }
    val submit = { if (valid) onConfirm(if (skipPassword) null else password.toCharArray()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painterResource(if (isExport) R.drawable.upload else R.drawable.download),
                contentDescription = null
            )
        },
        title = {
            Text(stringResource(if (isExport) R.string.export_title else R.string.import_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (isExport) R.string.export_body else R.string.import_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(stringResource(if (isExport) R.string.password_optional else R.string.password))
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isExport) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text(pluralStringResource(R.plurals.password_too_short, MIN_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (skipPassword) {
                    Text(
                        text = stringResource(R.string.export_unencrypted_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (isExport) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.confirm_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        isError = mismatch,
                        supportingText = if (mismatch) {
                            { Text(stringResource(R.string.password_mismatch)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = valid) {
                Text(
                    stringResource(
                        when {
                            skipPassword -> R.string.export_without_password
                            isExport -> R.string.next
                            else -> R.string.import_action
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Preview
@Composable
fun BackupPasswordDialogPreview() {
    SCTOTPTheme {
        BackupPasswordDialog(BackupMode.EXPORT, onConfirm = {}, onDismiss = {})
    }
}
