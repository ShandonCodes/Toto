package codes.shandon.toto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import codes.shandon.toto.ui.AddAccountDialog
import codes.shandon.toto.ui.BackupMode
import codes.shandon.toto.ui.BackupPasswordDialog
import codes.shandon.toto.ui.TOTPScreen
import codes.shandon.toto.ui.theme.TotoTheme
import kotlinx.coroutines.launch
import org.woheller69.freeDroidWarn.FreeDroidWarn
import java.security.GeneralSecurityException
import java.time.LocalDate

private const val TAG = "Toto"
class MainActivity : ComponentActivity() {
    private val totpViewModel: TOTPViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FreeDroidWarn.showWarningOnUpgrade(this, Build.VERSION.SDK_INT);
        enableEdgeToEdge()
        // Only on a fresh launch, so rotating doesn't re-offer the same link
        if (savedInstanceState == null) handleOtpAuthIntent(intent)
        setContent {
            TotoTheme {
                TOTPMainScreen(totpViewModel)
            }
        }
    }

    // singleTask: a link opened while we're running arrives here instead of a second instance
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOtpAuthIntent(intent)
    }

    // "Open with" from the camera app, Lens, or a browser for otpauth:// links
    private fun handleOtpAuthIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme.equals("otpauth", ignoreCase = true)) {
            totpViewModel.onOtpAuthLink(intent.data.toString())
        }
    }
}

@Composable
fun TOTPMainScreen(totpViewModel: TOTPViewModel = viewModel()) {
    val totpUiState by totpViewModel.uiState.collectAsState()
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appName = stringResource(R.string.app_name)

    var busy by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf<BackupMode?>(null) }
    // Held between the password dialog and the file picker result
    var pendingExportPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingImportContents by remember { mutableStateOf<String?>(null) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun authenticate(title: String, onSuccess: () -> Unit) {
        val currentActivity = activity ?: return
        totpViewModel.authInProgress = true
        AppLock.authenticate(currentActivity, title) { success ->
            totpViewModel.authInProgress = false
            if (success) onSuccess()
        }
    }

    // [task] returns the snackbar message to show, or null for none
    fun runBackupTask(task: suspend () -> String?) {
        scope.launch {
            busy = true
            val message = try {
                task()
            } catch (e: InvalidBackupException) {
                resources.getString(R.string.backup_invalid_file, appName)
            } catch (e: GeneralSecurityException) {
                // AEADBadTagException: wrong password or tampered file
                resources.getString(R.string.backup_wrong_password)
            } catch (e: Exception) {
                // Type only: parser messages can quote backup contents, which may include secrets
                Log.e(TAG, "Backup failed: ${e.javaClass.simpleName}")
                resources.getString(R.string.backup_failed)
            } finally {
                busy = false
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    // In-app scanner (CameraX + ZXing). The permission prompt is a translucent system dialog, so it
    // doesn't stop the activity and doesn't need expectExternalActivity().
    var scanning by rememberSaveable { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scanning = true else showMessage(resources.getString(R.string.camera_permission))
    }
    fun startScan() {
        val context = activity ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scanning = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
    // Locking hides everything, scanner included
    LaunchedEffect(totpUiState.isLocked) {
        if (totpUiState.isLocked) scanning = false
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        // Null password means the user chose an unencrypted export
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri != null) {
            runBackupTask {
                val count = totpViewModel.exportCodes(uri, password)
                password?.fill(' ')
                resources.getQuantityString(R.plurals.exported_codes, count, count)
            }
        } else {
            password?.fill(' ')
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runBackupTask {
                val contents = totpViewModel.readBackup(uri)
                if (BackupCrypto.isEncrypted(contents)) {
                    pendingImportContents = contents
                    passwordDialog = BackupMode.IMPORT
                    null
                } else {
                    val count = totpViewModel.importCodes(contents, null)
                    resources.getQuantityString(R.plurals.imported_codes, count, count)
                }
            }
        }
    }

    TOTPScreen(
        uiState = totpUiState,
        snackbarHostState = snackbarHostState,
        busy = busy,
        onAddCode = ::startScan,
        scanning = scanning && !totpUiState.isLocked,
        onScanResult = { raw ->
            scanning = false
            totpViewModel.addScannedCode(raw)
        },
        onScannerError = {
            scanning = false
            showMessage(resources.getString(R.string.camera_unavailable))
        },
        onCloseScanner = { scanning = false },
        onAddManualCode = totpViewModel::addManualCode,
        onDeleteCode = { totpViewModel.deleteCode(it.secret) },
        onRenameCode = { card, name -> totpViewModel.renameCode(card.secret, name) },
        onExport = {
            // Exporting reveals every secret, so re-confirm identity when the app is locked down
            if (totpUiState.appLockEnabled) {
                authenticate(resources.getString(R.string.export_prompt)) {
                    passwordDialog = BackupMode.EXPORT
                }
            } else {
                passwordDialog = BackupMode.EXPORT
            }
        },
        onImport = {
            totpViewModel.expectExternalActivity()
            // Backups may come back from cloud storage with a generic MIME type
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        },
        onToggleHideCodes = { totpViewModel.setHideCodes(!totpUiState.hideCodes) },
        onToggleAppLock = {
            when {
                totpUiState.appLockEnabled ->
                    authenticate(resources.getString(R.string.disable_lock_prompt)) {
                        totpViewModel.setAppLockEnabled(false)
                    }
                activity == null || !AppLock.canAuthenticate(activity) ->
                    showMessage(resources.getString(R.string.lock_unavailable))
                else ->
                    authenticate(resources.getString(R.string.enable_lock_prompt)) {
                        totpViewModel.setAppLockEnabled(true)
                    }
            }
        },
        onUnlock = {
            authenticate(resources.getString(R.string.unlock_prompt, appName)) { totpViewModel.unlock() }
        }
    )

    passwordDialog?.let { mode ->
        BackupPasswordDialog(
            mode = mode,
            onConfirm = { password ->
                passwordDialog = null
                when (mode) {
                    BackupMode.EXPORT -> {
                        pendingExportPassword = password
                        totpViewModel.expectExternalActivity()
                        exportLauncher.launch("${appName.lowercase()}-backup-${LocalDate.now()}.json")
                    }
                    BackupMode.IMPORT -> {
                        val contents = pendingImportContents ?: return@BackupPasswordDialog
                        pendingImportContents = null
                        runBackupTask {
                            val count = totpViewModel.importCodes(contents, password)
                            password?.fill(' ')
                            resources.getQuantityString(R.plurals.imported_codes, count, count)
                        }
                    }
                }
            },
            onDismiss = {
                passwordDialog = null
                pendingImportContents = null
            }
        )
    }

    // Accounts from "Open with" wait for confirmation, and for the app to be unlocked first
    totpUiState.pendingLinkAccount?.takeIf { !totpUiState.isLocked }?.let { account ->
        AddAccountDialog(
            account = account,
            onConfirm = totpViewModel::confirmLinkAccount,
            onDismiss = totpViewModel::dismissLinkAccount
        )
    }

    // One-off messages from the ViewModel (scan results, duplicates, ...). The snackbar runs in the
    // screen's scope: clearing the message changes this effect's key, which would cancel it here.
    LaunchedEffect(totpUiState.message) {
        val message = totpUiState.message ?: return@LaunchedEffect
        showMessage(resources.getString(message))
        totpViewModel.messageShown()
    }

    // Keep codes out of the recents thumbnail while app lock or code hiding is on
    val protectRecents = totpUiState.appLockEnabled || totpUiState.hideCodes
    LaunchedEffect(protectRecents) {
        activity?.setRecentsScreenshotEnabled(!protectRecents)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        totpViewModel.onAppStopped(activity?.isChangingConfigurations == true)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (totpViewModel.onAppStarted()) {
            authenticate(resources.getString(R.string.unlock_prompt, appName)) { totpViewModel.unlock() }
        }
    }

    LifecycleResumeEffect(totpViewModel) {
        totpViewModel.loopCodes()
        onPauseOrDispose {
            totpViewModel.pauseCodes()
        }
    }
}
