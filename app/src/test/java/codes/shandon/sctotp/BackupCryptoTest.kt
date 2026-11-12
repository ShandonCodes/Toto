package codes.shandon.sctotp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupCryptoTest {
    private val accounts = listOf(
        Account(issuer = "Google", username = "you@gmail.com", secret = "JBSWY3DPEHPK3PXP"),
        Account(issuer = "GitHub", username = "", secret = "KRSXG5CTMVRXEZLU")
    )

    @Test
    fun roundTrip() {
        val backup = BackupCrypto.encrypt(accounts, "correct horse".toCharArray())
        assertEquals(accounts, BackupCrypto.decrypt(backup, "correct horse".toCharArray()))
    }

    @Test
    fun secretsAreNotStoredInPlaintext() {
        val backup = BackupCrypto.encrypt(accounts, "correct horse".toCharArray())
        assertFalse(backup.contains("JBSWY3DPEHPK3PXP"))
        assertFalse(backup.contains("Google"))
    }

    @Test(expected = AEADBadTagException::class)
    fun wrongPasswordFails() {
        val backup = BackupCrypto.encrypt(accounts, "correct horse".toCharArray())
        BackupCrypto.decrypt(backup, "wrong horse".toCharArray())
    }

    @Test(expected = AEADBadTagException::class)
    fun tamperedCiphertextFails() {
        val root = JSONObject(BackupCrypto.encrypt(accounts, "correct horse".toCharArray()))
        val ciphertext = root.getString("ciphertext")
        val flipped = (if (ciphertext[0] == 'A') 'B' else 'A') + ciphertext.substring(1)
        BackupCrypto.decrypt(root.put("ciphertext", flipped).toString(), "correct horse".toCharArray())
    }

    @Test
    fun unencryptedRoundTrip() {
        val backup = BackupCrypto.encrypt(accounts, null)
        assertFalse(BackupCrypto.isEncrypted(backup))
        assertEquals(accounts, BackupCrypto.decrypt(backup, null))
    }

    @Test
    fun emptyPasswordMeansUnencrypted() {
        val backup = BackupCrypto.encrypt(accounts, CharArray(0))
        assertFalse(BackupCrypto.isEncrypted(backup))
        assertTrue(backup.contains("JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun encryptedBackupIsDetected() {
        assertTrue(BackupCrypto.isEncrypted(BackupCrypto.encrypt(accounts, "correct horse".toCharArray())))
    }

    @Test(expected = InvalidBackupException::class)
    fun encryptedBackupNeedsPassword() {
        BackupCrypto.decrypt(BackupCrypto.encrypt(accounts, "correct horse".toCharArray()), null)
    }

    @Test(expected = InvalidBackupException::class)
    fun rejectsNonBackupFiles() {
        BackupCrypto.decrypt("""{"hello": "world"}""", "x".toCharArray())
    }

    @Test(expected = InvalidBackupException::class)
    fun rejectsGarbage() {
        BackupCrypto.decrypt("not json at all", "x".toCharArray())
    }
}
