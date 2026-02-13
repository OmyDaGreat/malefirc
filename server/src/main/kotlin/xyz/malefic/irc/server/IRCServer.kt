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

class IRCServer(
    private val port: Int = 6667,
    private val serverName: String = "malefirc.local",
) {
    private val users = ConcurrentHashMap<String, ClientConnection>()
    private val channels = ConcurrentHashMap<String, IRCChannel>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
                        trailing = "sasl"
                    )
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
                            trailing = "sasl"
                        )
                    )
                } else {
                    sendMessage(
                        connection,
                        IRCMessage(
                            prefix = serverName,
                            command = "CAP",
                            params = listOf(user.nickname ?: "*", "NAK"),
                            trailing = message.trailing
                        )
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
                        trailing = if (user.authenticated) "sasl" else ""
                    )
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
                    IRCMessage(command = "AUTHENTICATE", params = listOf("+"))
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
                    )
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
                    )
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
                    val decoded = java.util.Base64.getDecoder().decode(buffer)
                    val parts = String(decoded).split('\u0000')
                    
                    if (parts.size == 3) {
                        val authzid = parts[0]  // Can be empty
                        val authcid = parts[1]  // Username
                        val passwd = parts[2]   // Password
                        
                        // Authenticate using our service
                        if (xyz.malefic.irc.server.auth.AuthenticationService.authenticate(authcid, passwd)) {
                            user.authenticated = true
                            user.accountName = authcid
                            
                            sendMessage(
                                connection,
                                IRCMessageBuilder.numericError(
                                    serverName,
                                    user.nickname ?: "*",
                                    IRCReply.RPL_SASLSUCCESS,
                                    "SASL authentication successful",
                                )
                            )
                            
                            // Send logged in notification
                            sendMessage(
                                connection,
                                IRCMessageBuilder.numericError(
                                    serverName,
                                    user.nickname ?: "*",
                                    IRCReply.RPL_LOGGEDIN,
                                    "${user.fullMask()} $authcid :You are now logged in as $authcid",
                                )
                            )
                        } else {
                            sendMessage(
                                connection,
                                IRCMessageBuilder.numericError(
                                    serverName,
                                    user.nickname ?: "*",
                                    IRCReply.RPL_SASLFAIL,
                                    "SASL authentication failed",
                                )
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
                            )
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
                        )
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
                    if (xyz.malefic.irc.server.auth.AuthenticationService.authenticate(username, user.password!!)) {
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
                )
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

        channelNames.forEach { channelName ->
            if (!channelName.startsWith('#')) return@forEach

            val channel = channels.computeIfAbsent(channelName) { IRCChannel(channelName) }
            channel.users[user.nickname!!] = user
            user.channels.add(channelName)

            // Broadcast join to all users in channel
            val joinMsg = IRCMessageBuilder.join(user.fullMask(), channelName)
            broadcastToChannel(channel, joinMsg)

            // Send topic
            if (channel.topic != null) {
                sendMessage(connection, IRCMessageBuilder.topic(serverName, user.nickname!!, channelName, channel.topic!!))
            } else {
                sendMessage(connection, IRCMessageBuilder.noTopic(serverName, user.nickname!!, channelName))
            }

            // Send names list
            val names = channel.users.keys.toList()
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
            if (channel == null || !channel.users.containsKey(user.nickname)) {
                sendMessage(
                    connection,
                    IRCMessageBuilder.numericError(
                        serverName,
                        user.nickname!!,
                        IRCReply.ERR_CANNOTSENDTOCHAN,
                        "$target :Cannot send to channel",
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
                isChannelMessage = true
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
                isChannelMessage = false
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
                val names = channel.users.keys.toList()
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
