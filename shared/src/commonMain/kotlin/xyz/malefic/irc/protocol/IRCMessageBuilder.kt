package xyz.malefic.irc.protocol

/**
 * Helper functions to create common IRC messages.
 *
 * This object provides convenience methods for constructing properly formatted
 * IRC protocol messages. Using these helpers reduces errors and ensures messages
 * follow RFC specifications.
 *
 * ## Usage
 * ```kotlin
 * // Send a welcome message
 * val welcomeMsg = IRCMessageBuilder.welcome("server.name", "alice")
 * sendMessage(connection, welcomeMsg)
 *
 * // Send a PRIVMSG
 * val msg = IRCMessageBuilder.privmsg("alice!user@host", "#channel", "Hello!")
 * broadcastToChannel(channel, msg)
 * ```
 *
 * @see IRCMessage for message structure
 * @see IRCCommand for command constants
 * @see IRCReply for reply codes
 */
object IRCMessageBuilder {
    /**
     * Creates RPL_WELCOME (001) message.
     *
     * @param serverName The server's name
     * @param nickname The user's nickname
     * @return Welcome message
     */
    fun welcome(
        serverName: String,
        nickname: String,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_WELCOME,
            params = listOf(nickname),
            trailing = "Welcome to the Internet Relay Network $nickname",
        )

    /**
     * Creates RPL_YOURHOST (002) message.
     *
     * @param serverName The server's name
     * @param nickname The user's nickname
     * @param version Server version string
     * @return Your host message
     */
    fun yourHost(
        serverName: String,
        nickname: String,
        version: String,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_YOURHOST,
            params = listOf(nickname),
            trailing = "Your host is $serverName, running version $version",
        )

    /**
     * Creates RPL_CREATED (003) message.
     *
     * @param serverName The server's name
     * @param nickname The user's nickname
     * @param date Server creation date
     * @return Created message
     */
    fun created(
        serverName: String,
        nickname: String,
        date: String,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_CREATED,
            params = listOf(nickname),
            trailing = "This server was created $date",
        )

    /**
     * Creates RPL_MYINFO (004) message with server information.
     *
     * @param serverName The server's name
     * @param nickname The user's nickname
     * @param version Server version
     * @return Server info message
     */
    fun myInfo(
        serverName: String,
        nickname: String,
        version: String,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_MYINFO,
            params = listOf(nickname, serverName, version, "o", "o"),
        )

    /**
     * Creates PRIVMSG command.
     *
     * @param from Sender's user mask (nick!user@host)
     * @param target Channel or nickname
     * @param message Message text
     * @return PRIVMSG message
     */
    fun privmsg(
        from: String,
        target: String,
        message: String,
    ): IRCMessage =
        IRCMessage(
            prefix = from,
            command = IRCCommand.PRIVMSG,
            params = listOf(target),
            trailing = message,
        )

    /**
     * Creates NOTICE command.
     *
     * @param from Sender's user mask (nick!user@host)
     * @param target Channel or nickname
     * @param message Notice text
     * @return NOTICE message
     */
    fun notice(
        from: String,
        target: String,
        message: String,
    ): IRCMessage =
        IRCMessage(
            prefix = from,
            command = IRCCommand.NOTICE,
            params = listOf(target),
            trailing = message,
        )

    /**
     * Creates JOIN command.
     *
     * @param user User mask (nick!user@host)
     * @param channel Channel name
     * @return JOIN message
     */
    fun join(
        user: String,
        channel: String,
    ): IRCMessage =
        IRCMessage(
            prefix = user,
            command = IRCCommand.JOIN,
            params = listOf(channel),
        )

    /**
     * Creates PART command.
     *
     * @param user User mask (nick!user@host)
     * @param channel Channel name
     * @param reason Optional part reason
     * @return PART message
     */
    fun part(
        user: String,
        channel: String,
        reason: String? = null,
    ): IRCMessage =
        IRCMessage(
            prefix = user,
            command = IRCCommand.PART,
            params = listOf(channel),
            trailing = reason,
        )

    /**
     * Creates QUIT command.
     *
     * @param user User mask (nick!user@host)
     * @param reason Optional quit reason
     * @return QUIT message
     */
    fun quit(
        user: String,
        reason: String? = null,
    ): IRCMessage =
        IRCMessage(
            prefix = user,
            command = IRCCommand.QUIT,
            trailing = reason ?: "Client Quit",
        )

    /**
     * Creates PING command.
     *
     * @param server Server name to ping
     * @return PING message
     */
    fun ping(server: String): IRCMessage =
        IRCMessage(
            command = IRCCommand.PING,
            params = listOf(server),
        )

    /**
     * Creates PONG response.
     *
     * @param server Server name
     * @param target Target of the pong
     * @return PONG message
     */
    fun pong(
        server: String,
        target: String,
    ): IRCMessage =
        IRCMessage(
            prefix = server,
            command = IRCCommand.PONG,
            params = listOf(server, target),
        )

    /**
     * Creates RPL_TOPIC (332) reply.
     *
     * @param serverName Server name
     * @param nickname User's nickname
     * @param channel Channel name
     * @param topic Channel topic
     * @return Topic message
     */
    fun topic(
        serverName: String,
        nickname: String,
        channel: String,
        topic: String,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_TOPIC,
            params = listOf(nickname, channel),
            trailing = topic,
        )

    /**
     * Creates RPL_NOTOPIC (331) reply.
     *
     * @param serverName Server name
     * @param nickname User's nickname
     * @param channel Channel name
     * @return No topic message
     */
    fun noTopic(
        serverName: String,
        nickname: String,
        channel: String,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_NOTOPIC,
            params = listOf(nickname, channel),
            trailing = "No topic is set",
        )

    /**
     * Creates RPL_NAMREPLY (353) reply with channel user list.
     *
     * @param serverName Server name
     * @param nickname User's nickname
     * @param channel Channel name
     * @param names List of nicknames (with prefixes like @, +)
     * @return Names reply message
     */
    fun nameReply(
        serverName: String,
        nickname: String,
        channel: String,
        names: List<String>,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_NAMREPLY,
            params = listOf(nickname, "=", channel),
            trailing = names.joinToString(" "),
        )

    /**
     * Creates RPL_ENDOFNAMES (366) reply.
     *
     * @param serverName Server name
     * @param nickname User's nickname
     * @param channel Channel name
     * @return End of names message
     */
    fun endOfNames(
        serverName: String,
        nickname: String,
        channel: String,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_ENDOFNAMES,
            params = listOf(nickname, channel),
            trailing = "End of /NAMES list",
        )

    /**
     * Creates ERROR message.
     *
     * @param message Error message text
     * @return ERROR message
     */
    fun error(message: String): IRCMessage =
        IRCMessage(
            command = IRCCommand.ERROR,
            trailing = message,
        )

    /**
     * Creates a numeric error reply.
     *
     * @param serverName Server name
     * @param nickname User's nickname
     * @param code Error code (e.g., ERR_NOSUCHNICK)
     * @param message Error message with parameters
     * @return Error reply message
     */
    fun numericError(
        serverName: String,
        nickname: String,
        code: String,
        message: String,
    ): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = code,
            params = listOf(nickname),
            trailing = message,
        )
}
