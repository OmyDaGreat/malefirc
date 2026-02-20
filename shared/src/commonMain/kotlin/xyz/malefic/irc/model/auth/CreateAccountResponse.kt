package xyz.malefic.irc.auth.model.auth

import kotlinx.serialization.Serializable

/**
 * Response to an IRC account creation request.
 *
 * @property succeeded `true` if the account was successfully created.
 */
@Serializable
data class CreateAccountResponse(
    val succeeded: Boolean
)
