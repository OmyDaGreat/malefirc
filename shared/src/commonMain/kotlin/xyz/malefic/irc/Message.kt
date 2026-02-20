package xyz.malefic.irc.model

import kotlinx.serialization.Serializable

/**
 * A simple chat message for use in the web client.
 *
 * @property username Nickname of the user who sent the message.
 * @property text Message body text.
 */
@Serializable
class Message(
    val username: String,
    val text: String,
)
