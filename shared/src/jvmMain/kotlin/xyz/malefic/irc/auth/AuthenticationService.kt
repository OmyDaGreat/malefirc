package xyz.malefic.irc.auth

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountEntity
import xyz.malefic.irc.auth.model.AccountTable
import xyz.malefic.irc.auth.util.PasswordHash

/**
 * Service for authenticating IRC users against the account database.
 *
 * All operations wrap database access in Exposed [transaction] blocks and are
 * safe to call from coroutines via `Dispatchers.IO`.
 *
 * @see PasswordHash for the BCrypt hashing used to verify passwords
 * @see xyz.malefic.irc.auth.config.DatabaseConfig for database initialisation
 */
object AuthenticationService {
    /**
     * Verifies a user's password against the stored BCrypt hash.
     *
     * If the credentials match, updates the account's `lastLogin` timestamp.
     *
     * @param username The username to authenticate.
     * @param password The plaintext password to verify.
     * @return `true` if the account exists and the password matches, `false` otherwise.
     */
    fun authenticate(username: String, password: String): Boolean =
        transaction {
            val account = AccountEntity.find { AccountTable.username eq username }.singleOrNull()
            if (account != null && PasswordHash.verify(password, account.password)) {
                account.lastLogin = System.currentTimeMillis()
                true
            } else {
                false
            }
        }
}
