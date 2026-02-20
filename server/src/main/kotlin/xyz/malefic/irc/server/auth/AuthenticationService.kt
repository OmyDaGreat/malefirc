package xyz.malefic.irc.server.auth

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
    fun authenticate(username: String, password: String): Boolean {
        return transaction {
            val account = AccountEntity.find { AccountTable.username eq username }.singleOrNull()
            
            if (account != null && PasswordHash.verify(password, account.password)) {
                // Update last login time
                account.lastLogin = System.currentTimeMillis()
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Checks whether an account with the given username exists.
     *
     * @param username The username to look up.
     * @return `true` if an account exists, `false` otherwise.
     */
    fun accountExists(username: String): Boolean {
        return transaction {
            AccountEntity.find { AccountTable.username eq username }.count() > 0
        }
    }
    
    /**
     * Returns the canonical account name for a registered user.
     *
     * @param username The username to look up.
     * @return The stored account username, or `null` if no account exists.
     */
    fun getAccountName(username: String): String? {
        return transaction {
            AccountEntity.find { AccountTable.username eq username }.singleOrNull()?.username
        }
    }
    
    /**
     * Registers a new user account.
     *
     * The password is hashed with BCrypt before being stored. If [email] is omitted,
     * the account is auto-verified.
     *
     * @param username Desired username (must be unique).
     * @param password Plaintext password to hash and store.
     * @param email Optional email address; if `null` the account is auto-verified.
     * @return `true` if the account was created, `false` if the username is already taken.
     */
    fun registerAccount(username: String, password: String, email: String? = null): Boolean {
        return try {
            transaction {
                if (accountExists(username)) {
                    return@transaction false
                }
                
                AccountEntity.new {
                    this.username = username
                    this.password = PasswordHash.hash(password)
                    this.email = email
                    this.isVerified = email == null // Auto-verify if no email
                }
                true
            }
        } catch (e: Exception) {
            println("Error registering account: ${e.message}")
            false
        }
    }
}
