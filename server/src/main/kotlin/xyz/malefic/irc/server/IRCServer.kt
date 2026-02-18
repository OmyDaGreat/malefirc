package xyz.malefic.irc.server

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.malefic.irc.protocol.IRCCommand
import xyz.malefic.irc.protocol.IRCMessage
import xyz.malefic.irc.protocol.IRCMessageBuilder
import xyz.malefic.irc.protocol.IRCReply
import java.util.concurrent.ConcurrentHashMap

/**
 * IRC Server implementation with full RFC 1459/2812 protocol support.
 *
 * Configuration via environment variables:
 * - IRC_PORT: Server port (default: 6667)
 * - IRC_SERVER_NAME: Server hostname (default: malefirc.local)
 * - IRC_OPER_NAME: Operator username (default: admin)
 * - IRC_OPER_PASSWORD: Operator password (default: adminpass)
 */
class IRCServer(
    private val port: Int = System.getenv("IRC_PORT")?.toIntOrNull() ?: 6667,
    private val serverName: String = System.getenv("IRC_SERVER_NAME") ?: "malefirc.local",
) {
    private val users = ConcurrentHashMap<String, ClientConnection>()
    private val channels = ConcurrentHashMap<String, IRCChannel>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Server operator credentials from environment or defaults
    private val operName = System.getenv("IRC_OPER_NAME") ?: "admin"
    private val operPassword = System.getenv("IRC_OPER_PASSWORD") ?: "adminpass"

    data class ClientConnection(
        val socket: Socket,
        val input: ByteReadChannel,
        val output: ByteWriteChannel,
        val user: IRCUser,
    )

    suspend fun start() {
        val serverSocket =
            aSocket(ActorSelectorManager(Dispatchers.IO))
                .tcp()
                .bind(port = port)

        println("IRC Server started on port $port")

        while (true) {
            val socket = serverSocket.accept()
            scope.launch {
                handleClient(socket)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = true)
        val user = IRCUser(hostname = socket.remoteAddress.toString())
        val connection = ClientConnection(socket, input, output, user)

        try {
            while (true) {
                val line = input.readLine() ?: break
                val message = IRCMessage.parse(line) ?: continue

                handleMessage(connection, message)
            }
        } catch (_: ClosedReceiveChannelException) {
            println("Client disconnected: ${user.nickname}")
        } catch (e: Exception) {
            println("Error handling client: ${e.message}")
            e.printStackTrace()
        } finally {
            disconnect(connection)
        }
    }

    private suspend fun handleMessage(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user

        when (message.command) {
            IRCCommand.PASS -> {
                handlePass(connection, message)
            }

            IRCCommand.CAP -> {
                handleCap(connection, message)
            }

            IRCCommand.AUTHENTICATE -> {
                handleAuthenticate(connection, message)
            }

            IRCCommand.NICK -> {
                handleNick(connection, message)
            }

            IRCCommand.USER -> {
                handleUser(connection, message)
            }

            IRCCommand.JOIN -> {
                handleJoin(connection, message)
            }

            IRCCommand.PART -> {
                handlePart(connection, message)
            }

            IRCCommand.PRIVMSG -> {
                handlePrivmsg(connection, message)
            }

            IRCCommand.QUIT -> {
                handleQuit(connection, message)
            }

            IRCCommand.PING -> {
                handlePing(connection, message)
            }

            IRCCommand.TOPIC -> {
                handleTopic(connection, message)
            }

            IRCCommand.NAMES -> {
                handleNames(connection, message)
            }

            IRCCommand.LIST -> {
                handleList(connection, message)
            }

            IRCCommand.WHO -> {
                handleWho(connection, message)
            }

            IRCCommand.MODE -> {
                handleMode(connection, message)
            }

            IRCCommand.WHOIS -> {
                handleWhois(connection, message)
            }

            IRCCommand.INVITE -> {
                handleInvite(connection, message)
            }

            IRCCommand.KICK -> {
                handleKick(connection, message)
            }

            IRCCommand.AWAY -> {
                handleAway(connection, message)
            }

            IRCCommand.OPER -> {
                handleOper(connection, message)
            }

            else -> {
                if (user.isRegistered()) {
                    sendMessage(
                        connection,
                        IRCMessageBuilder.numericError(
                            serverName,
                            user.nickname!!,
                            IRCReply.ERR_UNKNOWNCOMMAND,
                            "${message.command} :Unknown command",
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handlePass(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user

        if (user.registered) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname ?: "*",
                    IRCReply.ERR_ALREADYREGISTERED,
                    "You may not reregister",
                ),
            )
            return
        }

        val password = message.params.firstOrNull()
        if (password.isNullOrBlank()) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname ?: "*",
                    IRCReply.ERR_NEEDMOREPARAMS,
                    "PASS :Not enough parameters",
                ),
            )
            return
        }

        user.password = password
    }

    private suspend fun handleCap(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        val subcommand = message.params.firstOrNull()?.uppercase()

        when (subcommand) {
            "LS" -> {
                // List supported capabilities
                sendMessage(
                    connection,
                    IRCMessage(
                        prefix = serverName,
                        command = "CAP",
                        params = listOf(user.nickname ?: "*", "LS"),
                        trailing = "sasl",
                    ),
                )
            }

            "REQ" -> {
                // Client requests capabilities
                val caps = message.trailing?.split(" ") ?: emptyList()
                if (caps.contains("sasl")) {
                    sendMessage(
                        connection,
                        IRCMessage(
                            prefix = serverName,
                            command = "CAP",
                            params = listOf(user.nickname ?: "*", "ACK"),
                            trailing = "sasl",
                        ),
                    )
                } else {
                    sendMessage(
                        connection,
                        IRCMessage(
                            prefix = serverName,
                            command = "CAP",
                            params = listOf(user.nickname ?: "*", "NAK"),
                            trailing = message.trailing,
                        ),
                    )
                }
            }

            "END" -> {
                // Client done with capability negotiation
                // Nothing special needed
            }

            "LIST" -> {
                // List active capabilities
                sendMessage(
                    connection,
                    IRCMessage(
                        prefix = serverName,
                        command = "CAP",
                        params = listOf(user.nickname ?: "*", "LIST"),
                        trailing = if (user.authenticated) "sasl" else "",
                    ),
                )
            }
        }
    }

    private var saslBuffer = ConcurrentHashMap<ClientConnection, String>()

    private suspend fun handleAuthenticate(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        val data = message.params.firstOrNull() ?: ""

        when (data) {
            "PLAIN" -> {
                // Acknowledge PLAIN mechanism
                sendMessage(
                    connection,
                    IRCMessage(command = "AUTHENTICATE", params = listOf("+")),
                )
            }

            "+" -> {
                // Empty response, abort
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname ?: "*",
                        IRCReply.RPL_SASLABORTED,
                        "SASL authentication aborted",
                    ),
                )
                saslBuffer.remove(connection)
            }

            "*" -> {
                // Abort authentication
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname ?: "*",
                        IRCReply.RPL_SASLABORTED,
                        "SASL authentication aborted",
                    ),
                )
                saslBuffer.remove(connection)
            }

            else -> {
                // Base64 encoded authentication data
                try {
                    val buffer = saslBuffer.getOrDefault(connection, "") + data

                    // If data is exactly 400 characters, more chunks are coming
                    if (data.length == 400) {
                        saslBuffer[connection] = buffer
                        return
                    }

                    // Decode base64
                    val decoded =
                        java.util.Base64
                            .getDecoder()
                            .decode(buffer)
                    val parts = String(decoded).split('\u0000')

                    if (parts.size == 3) {
                        val authzid = parts[0] // Can be empty
                        val authcid = parts[1] // Username
                        val passwd = parts[2] // Password

                        // Authenticate using our service
                        if (xyz.malefic.irc.server.auth.AuthenticationService
                                .authenticate(authcid, passwd)
                        ) {
                            user.authenticated = true
                            user.accountName = authcid

                            sendMessage(
                                connection,
                                IRCMessageBuilder.numericError(
                                    serverName,
                                    user.nickname ?: "*",
                                    IRCReply.RPL_SASLSUCCESS,
                                    "SASL authentication successful",
                                ),
                            )

                            // Send logged in notification
                            sendMessage(
                                connection,
                                IRCMessageBuilder.numericError(
                                    serverName,
                                    user.nickname ?: "*",
                                    IRCReply.RPL_LOGGEDIN,
                                    "${user.fullMask()} $authcid :You are now logged in as $authcid",
                                ),
                            )
                        } else {
                            sendMessage(
                                connection,
                                IRCMessageBuilder.numericError(
                                    serverName,
                                    user.nickname ?: "*",
                                    IRCReply.RPL_SASLFAIL,
                                    "SASL authentication failed",
                                ),
                            )
                        }
                    } else {
                        sendMessage(
                            connection,
                            IRCMessageBuilder.numericError(
                                serverName,
                                user.nickname ?: "*",
                                IRCReply.RPL_SASLFAIL,
                                "SASL authentication failed: invalid format",
                            ),
                        )
                    }

                    saslBuffer.remove(connection)
                } catch (e: Exception) {
                    println("SASL authentication error: ${e.message}")
                    sendMessage(
                        connection,
                        IRCMessageBuilder.numericError(
                            serverName,
                            user.nickname ?: "*",
                            IRCReply.RPL_SASLFAIL,
                            "SASL authentication failed: ${e.message}",
                        ),
                    )
                    saslBuffer.remove(connection)
                }
            }
        }
    }

    private suspend fun handleNick(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        val newNick = message.params.firstOrNull()

        if (newNick.isNullOrBlank()) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname ?: "*",
                    IRCReply.ERR_NONICKNAMEGIVEN,
                    "No nickname given",
                ),
            )
            return
        }

        if (users.containsKey(newNick)) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname ?: "*",
                    IRCReply.ERR_NICKNAMEINUSE,
                    "$newNick :Nickname is already in use",
                ),
            )
            return
        }

        val oldNick = user.nickname
        user.nickname = newNick

        if (oldNick != null) {
            users.remove(oldNick)
        }
        users[newNick] = connection

        if (user.isRegistered() && !user.registered) {
            completeRegistration(connection)
        }
    }

    private suspend fun handleUser(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user

        if (user.username != null) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname ?: "*",
                    IRCReply.ERR_ALREADYREGISTERED,
                    "You may not reregister",
                ),
            )
            return
        }

        if (message.params.size < 3) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname ?: "*",
                    IRCReply.ERR_NEEDMOREPARAMS,
                    "USER :Not enough parameters",
                ),
            )
            return
        }

        user.username = message.params[0]
        user.realname = message.trailing ?: message.params.getOrNull(3) ?: ""

        if (user.isRegistered() && !user.registered) {
            completeRegistration(connection)
        }
    }

    private suspend fun completeRegistration(connection: ClientConnection) {
        val user = connection.user

        // If a password was provided via PASS, try to authenticate
        if (user.password != null && !user.authenticated) {
            val username = user.username ?: user.nickname
            if (username != null) {
                try {
                    if (xyz.malefic.irc.server.auth.AuthenticationService
                            .authenticate(username, user.password!!)
                    ) {
                        user.authenticated = true
                        user.accountName = username
                    }
                } catch (e: Exception) {
                    println("Authentication error for $username: ${e.message}")
                }
            }
        }

        user.registered = true

        val nick = user.nickname!!
        sendMessage(connection, IRCMessageBuilder.welcome(serverName, nick))
        sendMessage(connection, IRCMessageBuilder.yourHost(serverName, nick, "1.0.0"))
        sendMessage(connection, IRCMessageBuilder.created(serverName, nick, "2026-02-04"))
        sendMessage(connection, IRCMessageBuilder.myInfo(serverName, nick, "1.0.0"))

        // If authenticated, send logged in message
        if (user.authenticated && user.accountName != null) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    nick,
                    IRCReply.RPL_LOGGEDIN,
                    "${user.fullMask()} ${user.accountName} :You are now logged in as ${user.accountName}",
                ),
            )
        }
    }

    private suspend fun handleJoin(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        val channelNames = message.params.firstOrNull()?.split(",") ?: return
        val keys = message.params.getOrNull(1)?.split(",") ?: emptyList()

        channelNames.forEachIndexed { index, channelName ->
            if (!channelName.startsWith('#')) return@forEachIndexed

            val channel =
                channels.getOrPut(channelName) {
                    IRCChannel(channelName).apply {
                        // First user becomes operator
                        operators.add(user.nickname!!)
                    }
                }

            // Check if user is banned
            if (channel.isBanned(user.fullMask())) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_BANNEDFROMCHAN,
                        "$channelName :Cannot join channel (+b)",
                    ),
                )
                return@forEachIndexed
            }

            // Check invite-only mode
            if (channel.modes.contains('i') && !channel.inviteList.contains(user.nickname!!)) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_INVITEONLYCHAN,
                        "$channelName :Cannot join channel (+i)",
                    ),
                )
                return@forEachIndexed
            }

            // Check channel key
            if (channel.key != null) {
                val providedKey = keys.getOrNull(index)
                if (providedKey != channel.key) {
                    sendMessage(
                        connection,
                        IRCMessageBuilder.numericError(
                            serverName,
                            user.nickname!!,
                            IRCReply.ERR_BADCHANNELKEY,
                            "$channelName :Cannot join channel (+k)",
                        ),
                    )
                    return@forEachIndexed
                }
            }

            // Check user limit
            if (channel.userLimit != null && channel.users.size >= channel.userLimit!!) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_CHANNELISFULL,
                        "$channelName :Cannot join channel (+l)",
                    ),
                )
                return@forEachIndexed
            }

            channel.users[user.nickname!!] = user
            user.channels.add(channelName)

            // Remove from invite list if present
            channel.inviteList.remove(user.nickname!!)

            // Broadcast join to all users in channel
            val joinMsg = IRCMessageBuilder.join(user.fullMask(), channelName)
            broadcastToChannel(channel, joinMsg)

            // Send topic
            if (channel.topic != null) {
                sendMessage(connection, IRCMessageBuilder.topic(serverName, user.nickname!!, channelName, channel.topic!!))
            } else {
                sendMessage(connection, IRCMessageBuilder.noTopic(serverName, user.nickname!!, channelName))
            }

            // Send names list with prefixes
            val names =
                channel.users.keys.map { nick ->
                    when {
                        channel.isOperator(nick) -> "@$nick"
                        channel.isVoiced(nick) -> "+$nick"
                        else -> nick
                    }
                }
            sendMessage(connection, IRCMessageBuilder.nameReply(serverName, user.nickname!!, channelName, names))
            sendMessage(connection, IRCMessageBuilder.endOfNames(serverName, user.nickname!!, channelName))
        }
    }

    private suspend fun handlePart(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        val channelName = message.params.firstOrNull() ?: return
        val channel = channels[channelName] ?: return

        if (!channel.users.containsKey(user.nickname)) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NOTONCHANNEL,
                    "$channelName :You're not on that channel",
                ),
            )
            return
        }

        val partMsg = IRCMessageBuilder.part(user.fullMask(), channelName, message.trailing)
        broadcastToChannel(channel, partMsg)

        channel.users.remove(user.nickname)
        channel.operators.remove(user.nickname)
        channel.voiced.remove(user.nickname)
        user.channels.remove(channelName)

        if (channel.users.isEmpty()) {
            channels.remove(channelName)
        }
    }

    private suspend fun handlePrivmsg(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        val target = message.params.firstOrNull() ?: return
        val text = message.trailing ?: return

        if (target.startsWith('#')) {
            // Channel message
            val channel = channels[target]
            if (channel == null) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_NOSUCHCHANNEL,
                        "$target :No such channel",
                    ),
                )
                return
            }

            // Check if user is in channel (unless +n is not set)
            val inChannel = channel.users.containsKey(user.nickname)
            if (channel.modes.contains('n') && !inChannel) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_CANNOTSENDTOCHAN,
                        "$target :Cannot send to channel (+n)",
                    ),
                )
                return
            }

            // Check moderated mode
            if (channel.modes.contains('m') && !channel.canSpeak(user.nickname!!)) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_CANNOTSENDTOCHAN,
                        "$target :Cannot send to channel (+m)",
                    ),
                )
                return
            }

            val privmsg = IRCMessageBuilder.privmsg(user.fullMask(), target, text)
            broadcastToChannel(channel, privmsg, except = user.nickname)

            // Log channel message to history
            xyz.malefic.irc.server.history.MessageHistoryService.logMessage(
                sender = user.nickname!!,
                target = target,
                message = text,
                messageType = "PRIVMSG",
                isChannelMessage = true,
            )
        } else {
            // Private message
            val targetConn = users[target]
            if (targetConn == null) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_NOSUCHNICK,
                        "$target :No such nick/channel",
                    ),
                )
                return
            }

            val privmsg = IRCMessageBuilder.privmsg(user.fullMask(), target, text)
            sendMessage(targetConn, privmsg)

            // Log private message to history
            xyz.malefic.irc.server.history.MessageHistoryService.logMessage(
                sender = user.nickname!!,
                target = target,
                message = text,
                messageType = "PRIVMSG",
                isChannelMessage = false,
            )
        }
    }

    private suspend fun handleQuit(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        disconnect(connection, message.trailing ?: "Client quit")
    }

    private suspend fun handlePing(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val server = message.params.firstOrNull() ?: serverName
        sendMessage(connection, IRCMessageBuilder.pong(serverName, server))
    }

    private suspend fun handleTopic(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        val channelName = message.params.firstOrNull() ?: return
        val channel = channels[channelName]

        if (channel == null) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NOSUCHCHANNEL,
                    "$channelName :No such channel",
                ),
            )
            return
        }

        if (message.trailing != null) {
            // Setting topic
            // Check topic lock mode
            if (channel.modes.contains('t') && !channel.isOperator(user.nickname!!)) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_CHANOPRIVSNEEDED,
                        "$channelName :You're not channel operator",
                    ),
                )
                return
            }

            channel.topic = message.trailing
            val topicMsg =
                IRCMessage(
                    prefix = user.fullMask(),
                    command = IRCCommand.TOPIC,
                    params = listOf(channelName),
                    trailing = message.trailing,
                )
            broadcastToChannel(channel, topicMsg)
        } else {
            // Getting topic
            if (channel.topic != null) {
                sendMessage(connection, IRCMessageBuilder.topic(serverName, user.nickname!!, channelName, channel.topic!!))
            } else {
                sendMessage(connection, IRCMessageBuilder.noTopic(serverName, user.nickname!!, channelName))
            }
        }
    }

    private suspend fun handleNames(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        val channelName = message.params.firstOrNull()
        if (channelName != null) {
            val channel = channels[channelName]
            if (channel != null) {
                val names =
                    channel.users.keys.map { nick ->
                        when {
                            channel.isOperator(nick) -> "@$nick"
                            channel.isVoiced(nick) -> "+$nick"
                            else -> nick
                        }
                    }
                sendMessage(connection, IRCMessageBuilder.nameReply(serverName, user.nickname!!, channelName, names))
                sendMessage(connection, IRCMessageBuilder.endOfNames(serverName, user.nickname!!, channelName))
            }
        }
    }

    private suspend fun handleList(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        sendMessage(
            connection,
            IRCMessage(
                prefix = serverName,
                command = IRCReply.RPL_LISTSTART,
                params = listOf(user.nickname!!, "Channel", "Users  Name"),
            ),
        )

        channels.values.forEach { channel ->
            // Skip secret channels unless user is in them
            if (channel.modes.contains('s') && !channel.users.containsKey(user.nickname)) {
                return@forEach
            }

            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_LIST,
                    params = listOf(user.nickname!!, channel.name, channel.users.size.toString()),
                    trailing = channel.topic,
                ),
            )
        }

        sendMessage(
            connection,
            IRCMessage(
                prefix = serverName,
                command = IRCReply.RPL_LISTEND,
                params = listOf(user.nickname!!),
                trailing = "End of /LIST",
            ),
        )
    }

    private suspend fun handleWho(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        // Simplified WHO implementation
        val user = connection.user
        if (!user.isRegistered()) return

        val target = message.params.firstOrNull() ?: return

        if (target.startsWith('#')) {
            val channel = channels[target]
            channel?.users?.values?.forEach { channelUser ->
                sendMessage(
                    connection,
                    IRCMessage(
                        prefix = serverName,
                        command = "352",
                        params =
                            listOf(
                                user.nickname!!,
                                target,
                                channelUser.username ?: "unknown",
                                channelUser.hostname,
                                serverName,
                                channelUser.nickname ?: "unknown",
                                "H",
                            ),
                        trailing = "0 ${channelUser.realname}",
                    ),
                )
            }
        }

        sendMessage(
            connection,
            IRCMessage(
                prefix = serverName,
                command = "315",
                params = listOf(user.nickname!!, target),
                trailing = "End of /WHO list",
            ),
        )
    }

    private suspend fun handleMode(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        val target = message.params.firstOrNull() ?: return

        if (target.startsWith('#')) {
            handleChannelMode(connection, message, target)
        } else {
            handleUserMode(connection, message, target)
        }
    }

    private suspend fun handleUserMode(
        connection: ClientConnection,
        message: IRCMessage,
        targetNick: String,
    ) {
        val user = connection.user

        // Users can only set their own modes (except server operators)
        if (targetNick != user.nickname && !user.isOperator()) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_USERSDONTMATCH,
                    ":Cannot change mode for other users",
                ),
            )
            return
        }

        val modeString = message.params.getOrNull(1)
        if (modeString == null) {
            // Query mode
            val modes = user.modes.joinToString("")
            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_UMODEIS,
                    params = listOf(user.nickname!!, "+$modes"),
                ),
            )
            return
        }

        // Parse and apply mode changes
        var adding = true
        for (char in modeString) {
            when (char) {
                '+' -> {
                    adding = true
                }

                '-' -> {
                    adding = false
                }

                'i' -> {
                    if (adding) user.modes.add('i') else user.modes.remove('i')
                }

                'w' -> {
                    if (adding) user.modes.add('w') else user.modes.remove('w')
                }

                'o' -> {
                    // Only server operators can set +o, and only on themselves
                    if (adding && user.isOperator()) {
                        user.modes.add('o')
                    } else if (!adding) {
                        user.modes.remove('o')
                    }
                }

                else -> {
                    sendMessage(
                        connection,
                        IRCMessageBuilder.numericError(
                            serverName,
                            user.nickname!!,
                            IRCReply.ERR_UMODEUNKNOWNFLAG,
                            ":Unknown MODE flag",
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handleChannelMode(
        connection: ClientConnection,
        message: IRCMessage,
        channelName: String,
    ) {
        val user = connection.user
        val channel = channels[channelName]

        if (channel == null) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NOSUCHCHANNEL,
                    "$channelName :No such channel",
                ),
            )
            return
        }

        val modeString = message.params.getOrNull(1)
        if (modeString == null) {
            // Query channel modes
            val modeStr =
                buildString {
                    append('+')
                    channel.modes.sorted().forEach { append(it) }
                    if (channel.key != null) append('k')
                    if (channel.userLimit != null) append('l')
                }
            val params = mutableListOf<String>()
            if (channel.key != null) params.add(channel.key!!)
            if (channel.userLimit != null) params.add(channel.userLimit.toString())

            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_CHANNELMODEIS,
                    params = listOf(user.nickname!!, channelName, modeStr) + params,
                ),
            )
            return
        }

        // Need to be operator to change modes
        if (!channel.isOperator(user.nickname!!)) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_CHANOPRIVSNEEDED,
                    "$channelName :You're not channel operator",
                ),
            )
            return
        }

        // Parse mode changes
        var adding = true
        var paramIndex = 2
        val modeChanges = mutableListOf<String>()
        val modeParams = mutableListOf<String>()

        for (char in modeString) {
            when (char) {
                '+' -> {
                    adding = true
                }

                '-' -> {
                    adding = false
                }

                'o', 'v' -> {
                    // Operator/voice - requires parameter
                    val targetNick = message.params.getOrNull(paramIndex++)
                    if (targetNick != null && channel.users.containsKey(targetNick)) {
                        if (char == 'o') {
                            if (adding) channel.operators.add(targetNick) else channel.operators.remove(targetNick)
                        } else {
                            if (adding) channel.voiced.add(targetNick) else channel.voiced.remove(targetNick)
                        }
                        modeChanges.add("${if (adding) '+' else '-'}$char")
                        modeParams.add(targetNick)
                    }
                }

                'b' -> {
                    // Ban - requires parameter
                    val mask = message.params.getOrNull(paramIndex++)
                    if (mask != null) {
                        if (adding) {
                            channel.banList.add(mask)
                        } else {
                            channel.banList.remove(mask)
                        }
                        modeChanges.add("${if (adding) '+' else '-'}$char")
                        modeParams.add(mask)
                    } else {
                        // List bans
                        channel.banList.forEach { banMask ->
                            sendMessage(
                                connection,
                                IRCMessage(
                                    prefix = serverName,
                                    command = IRCReply.RPL_BANLIST,
                                    params = listOf(user.nickname!!, channelName, banMask),
                                ),
                            )
                        }
                        sendMessage(
                            connection,
                            IRCMessage(
                                prefix = serverName,
                                command = IRCReply.RPL_ENDOFBANLIST,
                                params = listOf(user.nickname!!, channelName),
                                trailing = "End of channel ban list",
                            ),
                        )
                    }
                }

                'k' -> {
                    // Channel key
                    if (adding) {
                        val key = message.params.getOrNull(paramIndex++)
                        if (key != null) {
                            channel.key = key
                            modeChanges.add("+$char")
                            modeParams.add(key)
                        }
                    } else {
                        channel.key = null
                        modeChanges.add("-$char")
                    }
                }

                'l' -> {
                    // User limit
                    if (adding) {
                        val limit = message.params.getOrNull(paramIndex++)?.toIntOrNull()
                        if (limit != null && limit > 0) {
                            channel.userLimit = limit
                            modeChanges.add("+$char")
                            modeParams.add(limit.toString())
                        }
                    } else {
                        channel.userLimit = null
                        modeChanges.add("-$char")
                    }
                }

                'm', 's', 'i', 't', 'n' -> {
                    // Simple boolean modes
                    if (adding) {
                        channel.modes.add(char)
                    } else {
                        channel.modes.remove(char)
                    }
                    modeChanges.add("${if (adding) '+' else '-'}$char")
                }
            }
        }

        // Broadcast mode change
        if (modeChanges.isNotEmpty()) {
            val modeMsg =
                IRCMessage(
                    prefix = user.fullMask(),
                    command = IRCCommand.MODE,
                    params = listOf(channelName, modeChanges.joinToString("")) + modeParams,
                )
            broadcastToChannel(channel, modeMsg)
        }
    }

    private suspend fun handleWhois(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        val targetNick = message.params.lastOrNull() ?: return
        val targetConn = users[targetNick]

        if (targetConn == null) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NOSUCHNICK,
                    "$targetNick :No such nick/channel",
                ),
            )
            return
        }

        val targetUser = targetConn.user

        // RPL_WHOISUSER
        sendMessage(
            connection,
            IRCMessage(
                prefix = serverName,
                command = IRCReply.RPL_WHOISUSER,
                params =
                    listOf(
                        user.nickname!!,
                        targetUser.nickname!!,
                        targetUser.username ?: "unknown",
                        targetUser.hostname,
                        "*",
                    ),
                trailing = targetUser.realname ?: "",
            ),
        )

        // RPL_WHOISCHANNELS
        if (targetUser.channels.isNotEmpty()) {
            val channelList =
                targetUser.channels.joinToString(" ") { channelName ->
                    val channel = channels[channelName]
                    when {
                        channel?.isOperator(targetUser.nickname!!) == true -> "@$channelName"
                        channel?.isVoiced(targetUser.nickname!!) == true -> "+$channelName"
                        else -> channelName
                    }
                }
            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_WHOISCHANNELS,
                    params = listOf(user.nickname!!, targetUser.nickname!!),
                    trailing = channelList,
                ),
            )
        }

        // RPL_WHOISSERVER
        sendMessage(
            connection,
            IRCMessage(
                prefix = serverName,
                command = IRCReply.RPL_WHOISSERVER,
                params = listOf(user.nickname!!, targetUser.nickname!!, serverName),
                trailing = "Malefirc IRC Server",
            ),
        )

        // RPL_WHOISOPERATOR
        if (targetUser.isOperator()) {
            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_WHOISOPERATOR,
                    params = listOf(user.nickname!!, targetUser.nickname!!),
                    trailing = "is an IRC operator",
                ),
            )
        }

        // RPL_WHOISACCOUNT
        if (targetUser.accountName != null) {
            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_WHOISACCOUNT,
                    params = listOf(user.nickname!!, targetUser.nickname!!, targetUser.accountName!!),
                    trailing = "is logged in as",
                ),
            )
        }

        // RPL_AWAY
        if (targetUser.isAway()) {
            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_AWAY,
                    params = listOf(user.nickname!!, targetUser.nickname!!),
                    trailing = targetUser.awayMessage ?: "is away",
                ),
            )
        }

        // RPL_ENDOFWHOIS
        sendMessage(
            connection,
            IRCMessage(
                prefix = serverName,
                command = IRCReply.RPL_ENDOFWHOIS,
                params = listOf(user.nickname!!, targetUser.nickname!!),
                trailing = "End of /WHOIS list",
            ),
        )
    }

    private suspend fun handleInvite(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        if (message.params.size < 2) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NEEDMOREPARAMS,
                    "INVITE :Not enough parameters",
                ),
            )
            return
        }

        val targetNick = message.params[0]
        val channelName = message.params[1]

        val channel = channels[channelName]
        if (channel == null) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NOSUCHCHANNEL,
                    "$channelName :No such channel",
                ),
            )
            return
        }

        // Must be on channel
        if (!channel.users.containsKey(user.nickname)) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NOTONCHANNEL,
                    "$channelName :You're not on that channel",
                ),
            )
            return
        }

        // Must be operator if channel is invite-only
        if (channel.modes.contains('i') && !channel.isOperator(user.nickname!!)) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_CHANOPRIVSNEEDED,
                    "$channelName :You're not channel operator",
                ),
            )
            return
        }

        val targetConn = users[targetNick]
        if (targetConn == null) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NOSUCHNICK,
                    "$targetNick :No such nick/channel",
                ),
            )
            return
        }

        // Check if user is already on channel
        if (channel.users.containsKey(targetNick)) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_USERONCHANNEL,
                    "$targetNick $channelName :is already on channel",
                ),
            )
            return
        }

        // Add to invite list
        channel.inviteList.add(targetNick)

        // Send invite notification to target
        sendMessage(
            targetConn,
            IRCMessage(
                prefix = user.fullMask(),
                command = IRCCommand.INVITE,
                params = listOf(targetNick, channelName),
            ),
        )

        // Confirm to inviter
        sendMessage(
            connection,
            IRCMessage(
                prefix = serverName,
                command = IRCReply.RPL_INVITING,
                params = listOf(user.nickname!!, targetNick, channelName),
            ),
        )
    }

    private suspend fun handleKick(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        if (message.params.size < 2) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NEEDMOREPARAMS,
                    "KICK :Not enough parameters",
                ),
            )
            return
        }

        val channelName = message.params[0]
        val targetNick = message.params[1]
        val reason = message.trailing ?: user.nickname!!

        val channel = channels[channelName]
        if (channel == null) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NOSUCHCHANNEL,
                    "$channelName :No such channel",
                ),
            )
            return
        }

        // Must be channel operator
        if (!channel.isOperator(user.nickname!!)) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_CHANOPRIVSNEEDED,
                    "$channelName :You're not channel operator",
                ),
            )
            return
        }

        // Target must be on channel
        if (!channel.users.containsKey(targetNick)) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_USERNOTINCHANNEL,
                    "$targetNick $channelName :They aren't on that channel",
                ),
            )
            return
        }

        // Broadcast kick
        val kickMsg =
            IRCMessage(
                prefix = user.fullMask(),
                command = IRCCommand.KICK,
                params = listOf(channelName, targetNick),
                trailing = reason,
            )
        broadcastToChannel(channel, kickMsg)

        // Remove user from channel
        val targetUser = channel.users[targetNick]
        channel.users.remove(targetNick)
        channel.operators.remove(targetNick)
        channel.voiced.remove(targetNick)
        targetUser?.channels?.remove(channelName)

        if (channel.users.isEmpty()) {
            channels.remove(channelName)
        }
    }

    private suspend fun handleAway(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        if (message.trailing.isNullOrBlank()) {
            // Unset away
            user.awayMessage = null
            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_UNAWAY,
                    params = listOf(user.nickname!!),
                    trailing = "You are no longer marked as being away",
                ),
            )
        } else {
            // Set away
            user.awayMessage = message.trailing
            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = IRCReply.RPL_NOWAWAY,
                    params = listOf(user.nickname!!),
                    trailing = "You have been marked as being away",
                ),
            )
        }
    }

    private suspend fun handleOper(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        if (message.params.size < 2) {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_NEEDMOREPARAMS,
                    "OPER :Not enough parameters",
                ),
            )
            return
        }

        val name = message.params[0]
        val password = message.params[1]

        // Validate against configured operator credentials (from environment variables)
        if (name == operName && password == operPassword) {
            user.modes.add('o')
            sendMessage(
                connection,
                IRCMessage(
                    prefix = serverName,
                    command = "381", // RPL_YOUREOPER
                    params = listOf(user.nickname!!),
                    trailing = "You are now an IRC operator",
                ),
            )
        } else {
            sendMessage(
                connection,
                IRCMessageBuilder.numericError(
                    serverName,
                    user.nickname!!,
                    IRCReply.ERR_PASSWDMISMATCH,
                    ":Password incorrect",
                ),
            )
        }
    }

    private suspend fun disconnect(
        connection: ClientConnection,
        reason: String = "Connection closed",
    ) {
        val user = connection.user

        if (user.nickname != null) {
            users.remove(user.nickname)

            // Remove from all channels and broadcast quit
            user.channels.toList().forEach { channelName ->
                channels[channelName]?.let { channel ->
                    channel.users.remove(user.nickname)
                    val quitMsg = IRCMessageBuilder.quit(user.fullMask(), reason)
                    broadcastToChannel(channel, quitMsg)

                    if (channel.users.isEmpty()) {
                        channels.remove(channelName)
                    }
                }
            }
        }

        try {
            withContext(Dispatchers.IO) {
                connection.socket.close()
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    private suspend fun sendMessage(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        try {
            connection.output.writeStringUtf8(message.toWireFormat())
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
        }
    }

    private suspend fun broadcastToChannel(
        channel: IRCChannel,
        message: IRCMessage,
        except: String? = null,
    ) {
        channel.users.values.forEach { user ->
            if (user.nickname != except) {
                users[user.nickname]?.let { conn ->
                    sendMessage(conn, message)
                }
            }
        }
    }
}
