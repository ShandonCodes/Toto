package codes.shandon.sctotp

import android.content.Context
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase


@Entity(tableName = "account")
data class Account(
    @ColumnInfo(name = "issuer") val issuer: String,
    @ColumnInfo(name = "username") val username: String,
    @PrimaryKey @ColumnInfo(name = "secret") val secret: String
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM account")
    suspend fun getAll(): List<Account>

    @Query("SELECT secret FROM account")
    suspend fun getAllSecrets(): List<String>

    @Query(
        """
        SELECT * FROM account
        WHERE issuer LIKE :issuer LIMIT 1
        """
    )
    suspend fun findByIssuer(issuer: String): Account

    @Query(
        """
        SELECT * FROM account
        WHERE username LIKE :username LIMIT 1
        """
    )
    suspend fun findByUsername(username: String): Account

    @Query(
        """
        SELECT * FROM account
        WHERE secret LIKE :secret LIMIT 1
        """
    )
    suspend fun findBySecret(secret: String): Account?

    @Insert
    suspend fun insertAll(vararg accounts: Account)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOne(account: Account)

    @Delete
    suspend fun delete(account: Account)
}

@Database(entities = [Account::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "totp_database")
                    .build()
                    .also { Instance = it }
            }
        }
    }
}