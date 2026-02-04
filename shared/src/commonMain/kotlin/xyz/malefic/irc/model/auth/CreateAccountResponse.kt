package xyz.malefic.irc.auth.model.auth

import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountResponse(
    val succeeded: Boolean
)
