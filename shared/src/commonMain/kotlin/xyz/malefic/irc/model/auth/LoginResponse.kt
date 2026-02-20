package xyz.malefic.irc.auth.model.auth

import kotlinx.serialization.Serializable

/**
 * Response to an IRC account login attempt.
 *
 * @property succeeded `true` if the credentials were valid and login succeeded.
 */
@Serializable
data class LoginResponse(
    val succeeded: Boolean
)
