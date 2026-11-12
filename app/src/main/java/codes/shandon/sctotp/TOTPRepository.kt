package codes.shandon.sctotp

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import java.util.Date

class AccountRepository(private val accountDao: AccountDao) {
    suspend fun insertAccount(account: Account) = accountDao.insertOne(account)

    suspend fun deleteAccount(account: Account) = accountDao.delete(account)

    suspend fun getAccountBySecret(secret: String): Account? = accountDao.findBySecret(secret)

    suspend fun getAllSecrets(): List<String> = accountDao.getAllSecrets()

    suspend fun getAllAccounts(): List<Account> = accountDao.getAll()
}


class TOTPRepository(){
    fun generateCodeLive(secret: String, timestamp: Date): String {
        return GoogleAuthenticator(secret.toByteArray(Charsets.UTF_8)).generate(timestamp)
    }

    // Codes rotate on fixed 30s windows counted from the epoch, not 30s after generation
    fun secondsLeft(timestamp: Date): Int {
        return PERIOD_SECONDS - ((timestamp.time / 1000) % PERIOD_SECONDS).toInt()
    }

    companion object {
        const val PERIOD_SECONDS = 30
    }
}

data class TOTPStatus (
    var code: String,
    var ttl: Date,
    var seconds: Int = 30
)
