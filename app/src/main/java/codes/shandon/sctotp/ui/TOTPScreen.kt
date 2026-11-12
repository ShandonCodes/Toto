package codes.shandon.sctotp.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.annotation.DrawableRes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codes.shandon.sctotp.R
import codes.shandon.sctotp.TOTPCode
import codes.shandon.sctotp.TOTPRepository
import codes.shandon.sctotp.TOTPUIState
import codes.shandon.sctotp.ui.theme.SCTOTPTheme
import kotlinx.coroutines.delay

// Codes turn to the error color once this many seconds remain
private const val URGENT_SECONDS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TOTPScreen(
    uiState: TOTPUIState,
    snackbarHostState: SnackbarHostState,
    onAddCode: () -> Unit,
    onDeleteCode: (TOTPCode) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleAppLock: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var pendingDelete by remember { mutableStateOf<TOTPCode?>(null) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (!uiState.isLocked) {
                        OverflowMenu(
                            canExport = uiState.cards.isNotEmpty() && !busy,
                            canImport = !busy,
                            appLockEnabled = uiState.appLockEnabled,
                            onExport = onExport,
                            onImport = onImport,
                            onToggleAppLock = onToggleAppLock
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (uiState.cards.isNotEmpty() && !uiState.isLocked) {
                ExtendedFloatingActionButton(
                    onClick = onAddCode,
                    expanded = fabExpanded,
                    icon = { Icon(painterResource(R.drawable.plus), contentDescription = null) },
                    text = { Text(stringResource(R.string.add_code)) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            uiState.cards.isEmpty() -> EmptyState(onAddCode, Modifier.padding(innerPadding))
            else -> CodeList(
                cards = uiState.cards,
                listState = listState,
                contentPadding = innerPadding,
                onLongClick = { pendingDelete = it }
            )
        }
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

@Composable
private fun CodeList(
    cards: List<TOTPCode>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onLongClick: (TOTPCode) -> Unit
) {
    val clockMillis by rememberClockMillis()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            // Leave room so the FAB never covers the last code
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(cards, key = { _, card -> card.secret }) { index, card ->
            TOTPCard(
                card = card,
                clockMillis = { clockMillis },
                shape = groupedShape(index, cards.size),
                onLongClick = { onLongClick(card) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
fun TOTPCard(
    card: TOTPCode,
    clockMillis: () -> Long,
    shape: Shape,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val urgent = card.seconds <= URGENT_SECONDS
    val codeColor by animateColorAsState(
        if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "codeColor"
    )

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .combinedClickable(
                    onClick = { copyCode(context, card.code) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
        ) {
            IssuerAvatar(card.issuer)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.issuer,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.username.isNotBlank() && card.username != card.issuer) {
                    Text(
                        text = card.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Crossfade(targetState = card.code, label = "code") { code ->
                    Text(
                        text = formatCode(code),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFeatureSettings = "tnum",
                            letterSpacing = 2.sp
                        ),
                        color = codeColor
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            val secondsLeft = pluralStringResource(R.plurals.seconds_left, card.seconds, card.seconds)
            CountdownPie(
                clockMillis = clockMillis,
                color = codeColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = secondsLeft }
            )
        }
    }
}

@Composable
private fun IssuerAvatar(issuer: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Text(
            text = issuer.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// Pie that drains as the current 30s window runs out. Reads the clock only in the draw phase so
// the smooth animation doesn't recompose the card.
@Composable
private fun CountdownPie(
    clockMillis: () -> Long,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val periodMillis = TOTPRepository.PERIOD_SECONDS * 1000L
        val remaining = 1f - (clockMillis() % periodMillis) / periodMillis.toFloat()
        drawCircle(trackColor)
        drawArc(color, startAngle = -90f, sweepAngle = -360f * remaining, useCenter = true)
    }
}

@Composable
private fun OverflowMenu(
    canExport: Boolean,
    canImport: Boolean,
    appLockEnabled: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleAppLock: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
            HorizontalDivider()
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
    val appName = stringResource(R.string.app_name)
    StatusMessage(
        icon = R.drawable.lock,
        title = stringResource(R.string.locked_title, appName),
        body = stringResource(R.string.locked_body),
        actionIcon = R.drawable.lock,
        actionLabel = stringResource(R.string.unlock),
        onAction = onUnlock,
        modifier = modifier
    )
}

@Composable
private fun EmptyState(onAddCode: () -> Unit, modifier: Modifier = Modifier) {
    StatusMessage(
        icon = R.drawable.qr_scanner,
        title = stringResource(R.string.empty_title),
        body = stringResource(R.string.empty_body),
        actionIcon = R.drawable.qr_scanner,
        actionLabel = stringResource(R.string.scan_qr_code),
        onAction = onAddCode,
        modifier = modifier
    )
}

// Centered icon, title, explanation and a single action. Shared by the empty and locked states.
@Composable
private fun StatusMessage(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    @DrawableRes actionIcon: Int,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            )
        }
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
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(
                painter = painterResource(actionIcon),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(actionLabel)
        }
    }
}

@Composable
private fun DeleteDialog(card: TOTPCode, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = if (card.username.isBlank() || card.username == card.issuer) card.issuer else "${card.issuer} (${card.username})"
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
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

// Expressive grouped list: large outer corners on the group, tight corners between items
private fun groupedShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 4.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
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
    SCTOTPTheme {
        TOTPScreen(
            uiState = TOTPUIState(cards = previewCards, isLoaded = true),
            snackbarHostState = remember { SnackbarHostState() },
            onAddCode = {},
            onDeleteCode = {},
            onExport = {},
            onImport = {},
            onToggleAppLock = {},
            onUnlock = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TOTPLockedPreview() {
    SCTOTPTheme {
        TOTPScreen(
            uiState = TOTPUIState(isLoaded = true, appLockEnabled = true, isLocked = true),
            snackbarHostState = remember { SnackbarHostState() },
            onAddCode = {},
            onDeleteCode = {},
            onExport = {},
            onImport = {},
            onToggleAppLock = {},
            onUnlock = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TOTPEmptyPreview() {
    SCTOTPTheme {
        TOTPScreen(
            uiState = TOTPUIState(isLoaded = true),
            snackbarHostState = remember { SnackbarHostState() },
            onAddCode = {},
            onDeleteCode = {},
            onExport = {},
            onImport = {},
            onToggleAppLock = {},
            onUnlock = {}
        )
    }
}
