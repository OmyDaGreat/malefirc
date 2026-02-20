package xyz.malefic.irc.auth.model.auth

import kotlinx.serialization.Serializable

/**
 * Credentials used for IRC account login requests.
 *
 * @property username The account username.
 * @property password The plaintext password (sent over TLS; never stored as-is).
 */
@Serializable
data class Account(
    val username: String,
    val password: String,
)
