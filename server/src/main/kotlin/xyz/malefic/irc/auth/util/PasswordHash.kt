package xyz.malefic.irc.auth.util

import org.mindrot.jbcrypt.BCrypt
import xyz.malefic.irc.server.auth.AuthenticationService

/**
 * Utility object for BCrypt password hashing and verification.
 *
 * Uses BCrypt with a randomly generated salt on each call to [hash], making
 * rainbow-table attacks infeasible.  Passwords are **never** stored in plaintext.
 *
 * The work factor is set by [BCrypt.gensalt] (default: 10), which is appropriate
 * for interactive authentication workloads.
 *
 * @see AuthenticationService for how hashed passwords are used during login
 */
object PasswordHash {
    /**
     * Hashes a plaintext password using BCrypt.
     *
     * @param password The plaintext password to hash.
     * @return A BCrypt hash string that includes the embedded salt.
     */
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     *
     * @param password The plaintext password to check.
     * @param hashed The stored BCrypt hash to compare against.
     * @return `true` if the password matches the hash.
     */
    fun verify(password: String, hashed: String): Boolean = BCrypt.checkpw(password, hashed)
}
