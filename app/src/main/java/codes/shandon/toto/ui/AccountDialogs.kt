package codes.shandon.toto.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codes.shandon.toto.Account
import codes.shandon.toto.MAX_NAME_LENGTH
import codes.shandon.toto.OtpAuth
import codes.shandon.toto.R
import codes.shandon.toto.TOTPCode

// "new code" menu: scan a QR code or type in a setup key
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCodeSheet(onScan: () -> Unit, onManual: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            SheetAction(R.drawable.qr_scanner, stringResource(R.string.scan_qr_code), MaterialTheme.colorScheme.onSurface, onScan)
            SheetAction(R.drawable.keyboard, stringResource(R.string.enter_setup_key), MaterialTheme.colorScheme.onSurface, onManual)
        }
    }
}

// Long-press menu for a card: rename or remove
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountOptionsSheet(
    card: TOTPCode,
    hideUsername: Boolean,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            Modifier
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            AccountHeader(card.issuer, card.username.takeUnless { hideUsername }, Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.padding(top = 12.dp))
            SheetAction(R.drawable.edit, stringResource(R.string.rename), MaterialTheme.colorScheme.onSurface, onRename)
            SheetAction(R.drawable.delete, stringResource(R.string.remove), MaterialTheme.colorScheme.error, onRemove)
        }
    }
}

@Composable
private fun SheetAction(@DrawableRes icon: Int, label: String, color: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

// Pill plus username, the same way a card shows an account
@Composable
private fun AccountHeader(issuer: String, username: String?, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Pill(issuer.lowercase(), accountColor(issuer))
        if (!username.isNullOrBlank() && username != issuer) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = username,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RenameDialog(card: TOTPCode, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // Start with the whole current name selected so typing replaces it
    var name by remember { mutableStateOf(TextFieldValue(card.issuer, TextRange(0, card.issuer.length))) }
    val focusRequester = remember { FocusRequester() }
    val valid = name.text.isNotBlank()
    val submit = { if (valid) onConfirm(name.text) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(painterResource(R.drawable.edit), contentDescription = null) },
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.rename_body))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.text.length <= MAX_NAME_LENGTH) name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                // Live preview of the new pill
                AccountHeader(name.text.ifBlank { card.issuer }, username = null)
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = valid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// Type in an account by hand. Validates the key as you go and only enables "add" when it's usable.
@Composable
fun ManualEntryDialog(onConfirm: (issuer: String, username: String, secret: String) -> Unit, onDismiss: () -> Unit) {
    var issuer by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val normalized = OtpAuth.normalizeSecret(secret)
    val problem = OtpAuth.secretProblem(normalized)
    // Bad characters are an error; being short is just a hint, since it's normal while typing
    val badCharacters = secret.isNotBlank() && problem == OtpAuth.SecretProblem.BAD_CHARACTERS
    val tooShort = secret.isNotBlank() && problem == OtpAuth.SecretProblem.TOO_SHORT
    val valid = issuer.isNotBlank() && problem == null
    val submit = { if (valid) onConfirm(issuer, username, secret) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(painterResource(R.drawable.keyboard), contentDescription = null) },
        title = { Text(stringResource(R.string.enter_setup_key)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.manual_body))
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { if (it.length <= MAX_NAME_LENGTH) issuer = it },
                    label = { Text(stringResource(R.string.service)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.account_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(stringResource(R.string.setup_key)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 1.sp),
                    // Password keyboard so Gboard doesn't learn or suggest the secret, but keep it
                    // visible so it can be checked against the site
                    visualTransformation = VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = badCharacters,
                    supportingText = when {
                        badCharacters -> {
                            { Text(stringResource(R.string.setup_key_invalid)) }
                        }
                        tooShort -> {
                            {
                                Text(
                                    pluralStringResource(
                                        R.plurals.setup_key_short,
                                        OtpAuth.MIN_MANUAL_SECRET_LENGTH,
                                        OtpAuth.MIN_MANUAL_SECRET_LENGTH
                                    )
                                )
                            }
                        }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (issuer.isNotBlank()) {
                    // Live preview of the card's pill
                    AccountHeader(issuer.trim(), username.trim())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = valid) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// Confirmation for an account that arrived from another app via an otpauth:// link
@Composable
fun AddAccountDialog(account: Account, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Toto(sleeping = false, modifier = Modifier.size(64.dp)) },
        title = { Text(stringResource(R.string.add_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AccountHeader(account.issuer, account.username)
                Text(stringResource(R.string.add_account_body))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
