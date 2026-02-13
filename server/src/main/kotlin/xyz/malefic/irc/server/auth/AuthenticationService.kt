package xyz.malefic.irc.server.auth

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountEntity
import xyz.malefic.irc.auth.model.AccountTable
import xyz.malefic.irc.auth.util.PasswordHash

/**
 * Service for handling IRC user authentication
 */
object AuthenticationService {
    /**
     * Verify a user's password against their account
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
     * Check if a user account exists
     */
    fun accountExists(username: String): Boolean {
        return transaction {
            AccountEntity.find { AccountTable.username eq username }.count() > 0
        }
    }
    
    /**
     * Get account name for a user (returns null if not authenticated)
     */
    fun getAccountName(username: String): String? {
        return transaction {
            AccountEntity.find { AccountTable.username eq username }.singleOrNull()?.username
        }
    }
    
    /**
     * Register a new account
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
