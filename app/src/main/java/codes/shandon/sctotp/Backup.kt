package codes.shandon.sctotp

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class InvalidBackupException(message: String) : Exception(message)

// Backup file, either password-encrypted:
// { "format": "sctotp-backup", "version": 1, "kdf": {...}, "iv": "...", "ciphertext": "..." }
// where the ciphertext is AES-256-GCM over a JSON array of accounts, keyed with PBKDF2-HMAC-SHA256,
// or unencrypted, with that array stored directly:
// { "format": "sctotp-backup", "version": 1, "accounts": [...] }
object BackupCrypto {
    private const val FORMAT = "sctotp-backup"
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 600_000
    // Refuse files asking for absurd work so a crafted backup can't hang the app
    private const val MAX_ITERATIONS = 10_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    // A null or empty password writes an unencrypted backup
    fun encrypt(accounts: List<Account>, password: CharArray?): String {
        if (password == null || password.isEmpty()) {
            return header().put("accounts", accountsToJson(accounts)).toString(2)
        }

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(accountsToJson(accounts).toString().toByteArray(Charsets.UTF_8))

        return header()
            .put("kdf", JSONObject()
                .put("algorithm", KDF)
                .put("iterations", ITERATIONS)
                .put("salt", encode(salt)))
            .put("iv", encode(iv))
            .put("ciphertext", encode(ciphertext))
            .toString(2)
    }

    // Throws InvalidBackupException for files that aren't ours
    fun isEncrypted(contents: String): Boolean = parse(contents).has("ciphertext")

    // Throws InvalidBackupException for files that aren't ours or encrypted ones without a password,
    // and javax.crypto.AEADBadTagException when the password is wrong or the file was modified
    fun decrypt(contents: String, password: CharArray?): List<Account> {
        val root = parse(contents)
        if (!root.has("ciphertext")) {
            return jsonToAccounts(root.optJSONArray("accounts") ?: throw InvalidBackupException("No accounts"))
        }
        if (password == null || password.isEmpty()) throw InvalidBackupException("Password required")

        val kdf = root.getJSONObject("kdf")
        if (kdf.getString("algorithm") != KDF) throw InvalidBackupException("Unsupported KDF")
        val iterations = kdf.getInt("iterations")
        if (iterations !in 1..MAX_ITERATIONS) throw InvalidBackupException("Bad iteration count")

        val key = deriveKey(password, decode(kdf.getString("salt")), iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decode(root.getString("iv"))))
        val plaintext = cipher.doFinal(decode(root.getString("ciphertext")))
        return jsonToAccounts(JSONArray(String(plaintext, Charsets.UTF_8)))
    }

    private fun header(): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)

    private fun parse(contents: String): JSONObject {
        val root = try {
            JSONObject(contents)
        } catch (e: Exception) {
            throw InvalidBackupException("Not JSON")
        }
        if (root.optString("format") != FORMAT) throw InvalidBackupException("Unknown format")
        if (root.optInt("version") != VERSION) throw InvalidBackupException("Unsupported version")
        return root
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val keyBytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun accountsToJson(accounts: List<Account>): JSONArray {
        val array = JSONArray()
        for (account in accounts) {
            array.put(JSONObject()
                .put("issuer", account.issuer)
                .put("username", account.username)
                .put("secret", account.secret))
        }
        return array
    }

    private fun jsonToAccounts(array: JSONArray): List<Account> {
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Account(
                issuer = item.getString("issuer"),
                username = item.optString("username"),
                secret = item.getString("secret")
            )
        }.filter { it.secret.isNotBlank() }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
        throw InvalidBackupException("Bad base64")
    }
}
