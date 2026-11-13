package codes.shandon.toto.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import codes.shandon.toto.R
import codes.shandon.toto.TOTPCode
import codes.shandon.toto.TOTPUIState
import codes.shandon.toto.ui.theme.TotoTheme
import kotlinx.coroutines.delay

// Codes turn to the error color once this many seconds remain
private const val URGENT_SECONDS = 5

// How long a tapped card stays revealed when hiding is on
private const val REVEAL_MILLIS = 15_000L

private enum class CodeDisplay { SHOWN, HIDDEN, COPIED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TOTPScreen(
    uiState: TOTPUIState,
    snackbarHostState: SnackbarHostState,
    onAddCode: () -> Unit,
    scanning: Boolean,
    onScanResult: (String) -> Unit,
    onScannerError: () -> Unit,
    onCloseScanner: () -> Unit,
    onAddManualCode: (issuer: String, username: String, secret: String) -> Unit,
    onDeleteCode: (TOTPCode) -> Unit,
    onRenameCode: (TOTPCode, String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleHideCodes: () -> Unit,
    onToggleAppLock: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false
) {
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var showAddChoice by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var pendingOptions by remember { mutableStateOf<TOTPCode?>(null) }
    var pendingRename by remember { mutableStateOf<TOTPCode?>(null) }
    var pendingDelete by remember { mutableStateOf<TOTPCode?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    // Back clears an active search before leaving the app
    BackHandler(enabled = query.isNotEmpty() && !uiState.isLocked) { query = "" }

    Scaffold(
        modifier = modifier,
        topBar = {
            // Single-row bar: title and menu share one line, no tall collapsing header
            TopAppBar(
                title = { OffsetTitle(stringResource(R.string.app_name).lowercase()) },
                actions = {
                    if (!uiState.isLocked) {
                        OverflowMenu(
                            canExport = uiState.cards.isNotEmpty() && !busy,
                            canImport = !busy,
                            appLockEnabled = uiState.appLockEnabled,
                            hideCodes = uiState.hideCodes,
                            onExport = onExport,
                            onImport = onImport,
                            onToggleHideCodes = onToggleHideCodes,
                            onToggleAppLock = onToggleAppLock
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (uiState.cards.isNotEmpty() && !uiState.isLocked) {
                val focusManager = LocalFocusManager.current
                ChunkyButton(onClick = {
                    focusManager.clearFocus()
                    showAddChoice = true
                }) {
                    Icon(painterResource(R.drawable.plus), contentDescription = stringResource(R.string.add_code))
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = expandHorizontally(),
                        exit = shrinkHorizontally()
                    ) {
                        Text(
                            text = stringResource(R.string.add_code),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    ) { innerPadding ->
        if (busy) {
            LinearProgressIndicator(
                Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxWidth()
            )
        }
        when {
            uiState.isLocked -> LockedState(onUnlock, Modifier.padding(innerPadding))
            !uiState.isLoaded -> Unit
            uiState.cards.isEmpty() -> EmptyState(
                onScan = onAddCode,
                onManual = { showManualEntry = true },
                modifier = Modifier.padding(innerPadding)
            )
            else -> CodeList(
                cards = uiState.cards,
                hideCodes = uiState.hideCodes,
                query = query,
                onQueryChange = { query = it },
                listState = listState,
                contentPadding = innerPadding,
                onLongClick = { pendingOptions = it }
            )
        }
    }

    // Drawn over everything else in this screen while active
    if (scanning) {
        ScannerScreen(
            onResult = onScanResult,
            onManual = {
                onCloseScanner()
                showManualEntry = true
            },
            onError = onScannerError,
            onClose = onCloseScanner
        )
    }

    if (showAddChoice) {
        AddCodeSheet(
            onScan = {
                showAddChoice = false
                onAddCode()
            },
            onManual = {
                showAddChoice = false
                showManualEntry = true
            },
            onDismiss = { showAddChoice = false }
        )
    }

    if (showManualEntry) {
        ManualEntryDialog(
            onConfirm = { issuer, username, secret ->
                showManualEntry = false
                onAddManualCode(issuer, username, secret)
            },
            onDismiss = { showManualEntry = false }
        )
    }

    pendingOptions?.let { card ->
        AccountOptionsSheet(
            card = card,
            hideUsername = uiState.hideCodes,
            onRename = {
                pendingOptions = null
                pendingRename = card
            },
            onRemove = {
                pendingOptions = null
                pendingDelete = card
            },
            onDismiss = { pendingOptions = null }
        )
    }

    pendingRename?.let { card ->
        RenameDialog(
            card = card,
            onConfirm = { name ->
                onRenameCode(card, name)
                pendingRename = null
            },
            onDismiss = { pendingRename = null }
        )
    }

    pendingDelete?.let { card ->
        DeleteDialog(
            card = card,
            onConfirm = {
                onDeleteCode(card)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeList(
    cards: List<TOTPCode>,
    hideCodes: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onLongClick: (TOTPCode) -> Unit
) {
    val clockMillis by rememberClockMillis()
    val focusManager = LocalFocusManager.current

    // Scrolling means the user has moved on from typing
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }
    val visibleCards = remember(cards, query) { filterCards(cards, query) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            // Leave room so the FAB never covers the last code
            bottom = contentPadding.calculateBottomPadding() + 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Start below the top bar (rather than padding the content) so the search box pins under it
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            // Taps on empty space drop focus; cards and the field consume their own taps first
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        item(key = "tagline") {
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        stickyHeader(key = "search") {
            PromptSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 4.dp)
            )
        }
        if (visibleCards.isEmpty()) {
            item(key = "no-matches") {
                Text(
                    text = stringResource(R.string.no_matches, query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .animateItem()
                )
            }
        }
        items(visibleCards, key = { it.secret }) { card ->
            TOTPCard(
                card = card,
                hidden = hideCodes,
                clockMillis = { clockMillis },
                onLongClick = { onLongClick(card) },
                modifier = Modifier.animateItem()
            )
        }
        item(key = "help") {
            HelpLine(
                items = listOf(
                    stringResource(R.string.help_tap) to
                        stringResource(if (hideCodes) R.string.help_reveal else R.string.help_copy),
                    stringResource(R.string.help_hold) to stringResource(R.string.help_remove)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun TOTPCard(
    card: TOTPCode,
    hidden: Boolean,
    clockMillis: () -> Long,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val shape = MaterialTheme.shapes.large
    val accent = accountColor(card.issuer)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val urgent = card.seconds <= URGENT_SECONDS
    val codeColor by animateColorAsState(
        if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        label = "codeColor"
    )
    val borderColor by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "border"
    )

    // When hiding is on, a tap reveals this one card for a while; it re-hides on a timer or as soon
    // as the app is backgrounded. A tap on a revealed card copies as usual.
    var revealed by remember(card.secret, hidden) { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(REVEAL_MILLIS)
            revealed = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { revealed = false }
    val showSecrets = !hidden || revealed

    // Show "copied ✓" in place of the code for a moment after tapping
    var copied by remember(card.secret) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // Accent stripe down the left edge
            .drawBehind { drawRect(accent, size = Size(5.dp.toPx(), size.height)) }
            .border(1.5.dp, borderColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    focusManager.clearFocus()
                    if (showSecrets) {
                        copyCode(context, card.code)
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        copied = true
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        revealed = true
                    }
                },
                onLongClick = {
                    focusManager.clearFocus()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(start = 21.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(card.issuer.lowercase(), accent)
            if (card.username.isNotBlank() && card.username != card.issuer) {
                Spacer(Modifier.width(8.dp))
                if (showSecrets) {
                    Text(
                        text = card.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Redacted(
                        length = card.username.length.coerceAtMost(20),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        description = stringResource(R.string.hidden_account),
                        blurRadius = 6.dp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val codeState = when {
                copied -> CodeDisplay.COPIED
                showSecrets -> CodeDisplay.SHOWN
                else -> CodeDisplay.HIDDEN
            }
            Crossfade(targetState = codeState, label = "codeState", modifier = Modifier.weight(1f)) { display ->
                if (display == CodeDisplay.COPIED) {
                    Text(
                        text = stringResource(R.string.copied),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (display == CodeDisplay.HIDDEN) {
                    Redacted(
                        length = card.code.length,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        ),
                        color = codeColor,
                        description = stringResource(R.string.hidden_code),
                        charset = "0123456789",
                        format = ::formatCode,
                        // Heavy enough that the filler can't be misread as a real code
                        blurRadius = 20.dp
                    )
                } else {
                    Crossfade(targetState = card.code, label = "code") { code ->
                        Text(
                            text = formatCode(code),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp
                            ),
                            color = codeColor
                        )
                    }
                }
            }
            Text(
                text = "${card.seconds}s",
                style = MaterialTheme.typography.labelLarge,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        val secondsLeft = pluralStringResource(R.plurals.seconds_left, card.seconds, card.seconds)
        BlockCountdown(
            clockMillis = clockMillis,
            urgent = urgent,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .semantics { contentDescription = secondsLeft }
        )
    }
}

@Composable
private fun OverflowMenu(
    canExport: Boolean,
    canImport: Boolean,
    appLockEnabled: Boolean,
    hideCodes: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleHideCodes: () -> Unit,
    onToggleAppLock: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val focusManager = LocalFocusManager.current
        IconButton(onClick = {
            focusManager.clearFocus()
            expanded = true
        }) {
            Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_codes)) },
                leadingIcon = { Icon(painterResource(R.drawable.upload), contentDescription = null) },
                enabled = canExport,
                onClick = {
                    expanded = false
                    onExport()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_codes)) },
                leadingIcon = { Icon(painterResource(R.drawable.download), contentDescription = null) },
                enabled = canImport,
                onClick = {
                    expanded = false
                    onImport()
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hide_codes)) },
                leadingIcon = { Icon(painterResource(R.drawable.visibility_off), contentDescription = null) },
                trailingIcon = { Switch(checked = hideCodes, onCheckedChange = null) },
                onClick = {
                    expanded = false
                    onToggleHideCodes()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_lock)) },
                leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null) },
                trailingIcon = { Switch(checked = appLockEnabled, onCheckedChange = null) },
                onClick = {
                    expanded = false
                    onToggleAppLock()
                }
            )
        }
    }
}

@Composable
private fun LockedState(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    TotoMessage(
        sleeping = true,
        title = stringResource(R.string.locked_title),
        body = stringResource(R.string.locked_body),
        actionIcon = R.drawable.lock,
        actionLabel = stringResource(R.string.unlock),
        onAction = onUnlock,
        modifier = modifier
    )
}

@Composable
private fun EmptyState(onScan: () -> Unit, onManual: () -> Unit, modifier: Modifier = Modifier) {
    TotoMessage(
        sleeping = false,
        title = stringResource(R.string.empty_title),
        body = stringResource(R.string.empty_body),
        actionIcon = R.drawable.qr_scanner,
        actionLabel = stringResource(R.string.scan_qr_code),
        onAction = onScan,
        secondaryLabel = stringResource(R.string.or_enter_setup_key),
        onSecondary = onManual,
        modifier = modifier
    )
}

// toto, a title, a line of explanation and one chunky action. Shared by the empty and locked states.
@Composable
private fun TotoMessage(
    sleeping: Boolean,
    title: String,
    body: String,
    actionIcon: Int,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        Toto(sleeping = sleeping, modifier = Modifier.size(160.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        ChunkyButton(onClick = onAction) {
            Icon(
                painter = painterResource(actionIcon),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
        if (secondaryLabel != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

@Composable
private fun DeleteDialog(card: TOTPCode, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = if (card.username.isBlank() || card.username == card.issuer) card.issuer else "${card.issuer} (${card.username})"
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                painterResource(R.drawable.delete),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.remove_title)) },
        text = { Text(stringResource(R.string.remove_body, name)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// Case-insensitive match on issuer or username; a blank query shows everything
private fun filterCards(cards: List<TOTPCode>, query: String): List<TOTPCode> {
    val needle = query.trim()
    if (needle.isEmpty()) return cards
    return cards.filter {
        it.issuer.contains(needle, ignoreCase = true) || it.username.contains(needle, ignoreCase = true)
    }
}

// "123456" -> "123 456"
private fun formatCode(code: String): String {
    val half = code.length / 2
    return code.substring(0, half) + " " + code.substring(half)
}

// Android 13+ shows its own copy confirmation, so no snackbar here. The clip is flagged
// sensitive so the code is hidden in the clipboard preview.
private fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("Verification code", code)
    clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    clipboard.setPrimaryClip(clip)
}

@Composable
private fun rememberClockMillis(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(50L)
    }
}

private val previewCards = listOf(
    TOTPCode("a", "Google", "you@gmail.com", "482913", 18),
    TOTPCode("b", "GitHub", "octocat", "105772", 18),
    TOTPCode("c", "Discord", "", "993104", 4)
)

@Preview(showBackground = true)
@Composable
fun TOTPScreenPreview() {
    TotoTheme(darkTheme = true) {
        TOTPScreen(
            uiState = TOTPUIState(cards = previewCards, isLoaded = true),
            snackbarHostState = remember { SnackbarHostState() },
            onAddCode = {},
            scanning = false,
            onScanResult = {},
            onScannerError = {},
            onCloseScanner = {},
            onAddManualCode = { _, _, _ -> },
            onDeleteCode = {},
            onRenameCode = { _, _ -> },
            onExport = {},
            onImport = {},
            onToggleHideCodes = {},
            onToggleAppLock = {},
            onUnlock = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TOTPScreenLightPreview() {
    TotoTheme(darkTheme = false) {
        TOTPScreen(
            uiState = TOTPUIState(cards = previewCards, isLoaded = true),
            snackbarHostState = remember { SnackbarHostState() },
            onAddCode = {},
            scanning = false,
            onScanResult = {},
            onScannerError = {},
            onCloseScanner = {},
            onAddManualCode = { _, _, _ -> },
            onDeleteCode = {},
            onRenameCode = { _, _ -> },
            onExport = {},
            onImport = {},
            onToggleHideCodes = {},
            onToggleAppLock = {},
            onUnlock = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TOTPLockedPreview() {
    TotoTheme(darkTheme = true) {
        TOTPScreen(
            uiState = TOTPUIState(isLoaded = true, appLockEnabled = true, isLocked = true),
            snackbarHostState = remember { SnackbarHostState() },
            onAddCode = {},
            scanning = false,
            onScanResult = {},
            onScannerError = {},
            onCloseScanner = {},
            onAddManualCode = { _, _, _ -> },
            onDeleteCode = {},
            onRenameCode = { _, _ -> },
            onExport = {},
            onImport = {},
            onToggleHideCodes = {},
            onToggleAppLock = {},
            onUnlock = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TOTPEmptyPreview() {
    TotoTheme(darkTheme = true) {
        TOTPScreen(
            uiState = TOTPUIState(isLoaded = true),
            snackbarHostState = remember { SnackbarHostState() },
            onAddCode = {},
            scanning = false,
            onScanResult = {},
            onScannerError = {},
            onCloseScanner = {},
            onAddManualCode = { _, _, _ -> },
            onDeleteCode = {},
            onRenameCode = { _, _ -> },
            onExport = {},
            onImport = {},
            onToggleHideCodes = {},
            onToggleAppLock = {},
            onUnlock = {}
        )
    }
}
