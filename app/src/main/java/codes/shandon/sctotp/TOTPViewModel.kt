package codes.shandon.sctotp

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

private const val TAG = "SC_TOTP"
private const val PREF_APP_LOCK = "app_lock"

class TOTPViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        prefs.getBoolean(PREF_APP_LOCK, false).let { lockEnabled ->
            TOTPUIState(appLockEnabled = lockEnabled, isLocked = lockEnabled)
        }
    )
    val uiState: StateFlow<TOTPUIState> = _uiState.asStateFlow()

    private val accountRepo: AccountRepository =
        AccountRepository(AppDatabase.getDatabase(application).accountDao())
    private val totpRepo: TOTPRepository = TOTPRepository()

    private var accounts: List<Account> = emptyList()
    val secrets: MutableMap<String, TOTPStatus> = mutableMapOf()

    private var loopJob: Job? = null

    // Set while we're showing the system auth prompt or our own scanner/file picker, so the
    // activity stopping underneath them doesn't relock the app
    var authInProgress = false
    private var externalActivityPending = false

    // Parses an otpauth://totp/Issuer:username?secret=...&issuer=... URI and stores the account.
    // Returns false if the scanned value isn't a usable otpauth URI.
    fun addCode(scannedCode: String?): Boolean {
        if (scannedCode == null) return false
        val uri = scannedCode.toUri()
        if (uri.scheme != "otpauth") {
            Log.w(TAG, "Scanned QR code is not an otpauth URI")
            return false
        }

        val path = uri.path?.trimStart('/')?.split(":").orEmpty()
        val username = path.lastOrNull().orEmpty()
        // The label is either "Issuer:username" or just "username"
        val issuer = uri.getQueryParameter("issuer") ?: path.takeIf { it.size > 1 }?.first() ?: username
        val secret = uri.getQueryParameter("secret")

        if (issuer == null || secret == null) return false
        viewModelScope.launch {
            if (accountRepo.getAccountBySecret(secret) == null) {
                Log.i(TAG, "Adding code...")
                accountRepo.insertAccount(Account(issuer = issuer, username = username, secret = secret))
                loadSecrets()
                publishCards()
            }
        }
        return true
    }

    fun deleteCode(secret: String) {
        viewModelScope.launch {
            accountRepo.getAccountBySecret(secret)?.let { accountRepo.deleteAccount(it) }
            secrets.remove(secret)
            accounts = accounts.filter { it.secret != secret }
            publishCards()
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_APP_LOCK, enabled).apply()
        _uiState.update { it.copy(appLockEnabled = enabled, isLocked = false) }
    }

    fun unlock() {
        _uiState.update { it.copy(isLocked = false) }
    }

    fun expectExternalActivity() {
        externalActivityPending = true
    }

    fun onAppStopped(changingConfigurations: Boolean) {
        if (_uiState.value.appLockEnabled && !changingConfigurations &&
            !authInProgress && !externalActivityPending
        ) {
            _uiState.update { it.copy(isLocked = true) }
        }
    }

    // Returns true if the unlock prompt should be shown
    fun onAppStarted(): Boolean {
        externalActivityPending = false
        return _uiState.value.isLocked && !authInProgress
    }

    // Writes a backup of every account to [uri], encrypted unless [password] is null or empty.
    // Returns how many were exported.
    suspend fun exportCodes(uri: Uri, password: CharArray?): Int {
        val allAccounts = accountRepo.getAllAccounts()
        withContext(Dispatchers.Default) {
            val contents = BackupCrypto.encrypt(allAccounts, password)
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(contents.toByteArray(Charsets.UTF_8))
                }
            }
        }
        return allAccounts.size
    }

    // Reads a backup file's contents so the UI can check whether it needs a password
    suspend fun readBackup(uri: Uri): String = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }

    // Adds any accounts from the backup [contents] that we don't already have.
    // [password] is only needed for encrypted backups. Returns how many new accounts were added.
    suspend fun importCodes(contents: String, password: CharArray?): Int {
        val imported = withContext(Dispatchers.Default) { BackupCrypto.decrypt(contents, password) }
        var added = 0
        for (account in imported) {
            if (accountRepo.getAccountBySecret(account.secret) == null) {
                accountRepo.insertAccount(account)
                added++
            }
        }
        loadSecrets()
        publishCards()
        return added
    }

    suspend fun loadSecrets() {
        accounts = accountRepo.getAllAccounts()
        val timestamp = Date(System.currentTimeMillis())
        for (account in accounts) {
            secrets[account.secret] = TOTPStatus(
                totpRepo.generateCodeLive(account.secret, timestamp),
                timestamp,
                totpRepo.secondsLeft(timestamp)
            )
        }
        Log.i(TAG, "Loaded secrets: ${secrets.size}")
    }

    fun loopCodes() {
        if (loopJob?.isActive == true) return
        Log.i(TAG, "Looping codes...")

        loopJob = viewModelScope.launch {
            loadSecrets()
            while (isActive) {
                val timestamp = Date(System.currentTimeMillis())
                val seconds = totpRepo.secondsLeft(timestamp)
                for ((secret, status) in secrets) {
                    // Regenerate once the current 30s window rolls over
                    val code = if (seconds > status.seconds) {
                        totpRepo.generateCodeLive(secret, timestamp)
                    } else {
                        status.code
                    }
                    secrets[secret] = TOTPStatus(code, timestamp, seconds)
                }
                publishCards()
                delay(1000L)
            }
        }
    }

    fun pauseCodes() {
        Log.i(TAG, "Pausing codes...")
        loopJob?.cancel()
        loopJob = null
    }

    private fun publishCards() {
        val cards = accounts.mapNotNull { account ->
            secrets[account.secret]?.let { status ->
                TOTPCode(account.secret, account.issuer, account.username, status.code, status.seconds)
            }
        }
        _uiState.update { it.copy(isLoaded = true, cards = cards) }
    }
}

data class TOTPCode(
    val secret: String,
    val issuer: String,
    val username: String,
    val code: String,
    val seconds: Int
)

data class TOTPUIState(
    val cards: List<TOTPCode> = emptyList(),
    val isLoaded: Boolean = false,
    val appLockEnabled: Boolean = false,
    val isLocked: Boolean = false
)
