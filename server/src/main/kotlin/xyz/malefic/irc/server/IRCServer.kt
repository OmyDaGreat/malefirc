package xyz.malefic.irc.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.malefic.irc.protocol.IRCCommand
import xyz.malefic.irc.protocol.IRCMessage
import xyz.malefic.irc.protocol.IRCMessageBuilder
import xyz.malefic.irc.protocol.IRCReply
import xyz.malefic.irc.server.tls.TLSConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * IRC Server implementation with full RFC 1459/2812 protocol support, including SSL/TLS.
 *
 * Two listeners are started when TLS is enabled:
 * - Plain TCP on [port] (default 6667).
 * - Dedicated TLS on [tlsPort] (default 6697) when [tlsEnabled] is `true`.
 *
 * ## Configuration via environment variables
 * | Variable | Default | Description |
 * |---|---|---|
 * | `IRC_PORT` | `6667` | Plain TCP port |
 * | `IRC_SERVER_NAME` | `malefirc.local` | Server hostname shown in messages |
 * | `IRC_OPER_NAME` | `admin` | OPER command username |
 * | `IRC_OPER_PASSWORD` | `adminpass` | OPER command password |
 * | `IRC_TLS_ENABLED` | `false` | Enable dedicated TLS port |
 * | `IRC_TLS_PORT` | `6697` | Dedicated TLS port |
 *
 * @param port Plain TCP port (default from `IRC_PORT` env var or 6667).
 * @param serverName Hostname used in server messages (default from `IRC_SERVER_NAME` or `malefirc.local`).
 * @param tlsEnabled Whether to start the dedicated TLS listener.
 * @param tlsPort Port for the dedicated TLS listener.
 *
 * @see TLSConfig for TLS keystore configuration
 * @see IRCUser for user state
 * @see IRCChannel for channel state
 */
class IRCServer(
    private val port: Int = System.getenv("IRC_PORT")?.toIntOrNull() ?: 6667,
    private val serverName: String = System.getenv("IRC_SERVER_NAME") ?: "malefirc.local",
    private val tlsEnabled: Boolean = TLSConfig.tlsEnabled,
    private val tlsPort: Int = TLSConfig.tlsPort,
) {
    private val users = ConcurrentHashMap<String, ClientConnection>()
    private val channels = ConcurrentHashMap<String, IRCChannel>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Server operator credentials sourced from environment variables. */
    private val operName = System.getenv("IRC_OPER_NAME") ?: "admin"

    /** Server operator password sourced from environment variables. */
    private val operPassword = System.getenv("IRC_OPER_PASSWORD") ?: "adminpass"

    /** Regex that matches `@nickname` mention patterns in message text. */
    private val mentionRegex = Regex("""@([A-Za-z0-9_\-\[\]\\{}^|]+)""")

    /**
     * Represents an active client connection.
     *
     * Wraps the underlying TCP or SSL socket together with buffered I/O streams.
     *
     * @property socket The underlying Java [Socket] (plain TCP or [SSLSocket]).
     * @property reader Buffered reader for incoming IRC lines.
     * @property writer Print writer for outgoing IRC messages.
     * @property user The [IRCUser] state associated with this connection.
     * @property isTLS Whether this connection is using TLS encryption.
     */
    class ClientConnection(
        var socket: Socket,
        var reader: BufferedReader,
        var writer: PrintWriter,
        val user: IRCUser,
        var isTLS: Boolean = false,
    )

    /**
     * Starts the server, binding to the configured plain and (optionally) TLS ports.
     *
     * If [tlsEnabled] is `true`, a separate coroutine is launched to accept TLS connections
     * on [tlsPort] concurrently with the plain listener on [port].
     */
    suspend fun start() {
        if (tlsEnabled) {
            scope.launch { startTLSListener() }
        }

        val serverSocket = withContext(Dispatchers.IO) { ServerSocket(port) }
        println("IRC Server started on port $port")

        while (true) {
            val socket = withContext(Dispatchers.IO) { serverSocket.accept() }
            scope.launch { handleClient(socket) }
        }
    }

    /**
     * Starts the dedicated TLS listener on [tlsPort].
     *
     * Accepts [SSLSocket] connections from an [SSLServerSocket] and handles each in a
     * separate coroutine. The SSL handshake is completed before [handleClient] is called.
     */
    private suspend fun startTLSListener() {
        val sslContext =
            try {
                TLSConfig.createSSLContext()
            } catch (e: Exception) {
                println("Failed to initialise TLS context: ${e.message}. TLS port will not be available.")
                return
            }

        if (sslContext == null) {
            println("TLS context could not be created. TLS port will not be available.")
            return
        }

        val sslServerSocket =
            withContext(Dispatchers.IO) {
                sslContext.serverSocketFactory.createServerSocket(tlsPort) as SSLServerSocket
            }
        println("IRC Server TLS started on port $tlsPort")

        while (true) {
            val sslSocket = withContext(Dispatchers.IO) { sslServerSocket.accept() as SSLSocket }
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { sslSocket.startHandshake() }
                    handleClient(sslSocket, isTLS = true)
                } catch (e: Exception) {
                    println("TLS handshake failed: ${e.message}")
                    withContext(Dispatchers.IO) { sslSocket.close() }
                }
            }
        }
    }

    /**
     * Handles an individual client connection for its entire lifetime.
     *
     * Reads IRC lines from [socket], parses them, and dispatches to the appropriate
     * command handler. Cleans up via [disconnect] when the connection closes.
     *
     * @param socket The accepted client socket (plain [Socket] or [SSLSocket]).
     * @param isTLS Whether the connection was accepted on the TLS port.
     */
    private suspend fun handleClient(
        socket: Socket,
        isTLS: Boolean = false,
    ) {
        val reader =
            withContext(Dispatchers.IO) {
                BufferedReader(InputStreamReader(socket.inputStream))
            }
        val writer =
            withContext(Dispatchers.IO) {
                PrintWriter(socket.outputStream, true)
            }
        val hostname = socket.inetAddress?.hostAddress ?: socket.remoteSocketAddress.toString()
        val user = IRCUser(hostname = hostname)
        val connection = ClientConnection(socket, reader, writer, user, isTLS = isTLS)

        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { connection.reader.readLine() } ?: break
                val message = IRCMessage.parse(line) ?: continue
                handleMessage(connection, message)
            }
        } catch (e: Exception) {
            if (e.message?.contains("Connection reset") == true ||
                e.message?.contains("Broken pipe") == true ||
                e.message?.contains("Socket closed") == true
            ) {
                println("Client disconnected: ${user.nickname}")
            } else {
                println("Error handling client ${user.nickname}: ${e.message}")
            }
        } finally {
            disconnect(connection)
        }
    }

    /**
     * Dispatches a single parsed [IRCMessage] to the appropriate command handler.
     *
     * Commands are matched case-insensitively using [IRCCommand] constants. Unknown
     * commands receive an [IRCReply.ERR_UNKNOWNCOMMAND] response if the user is registered.
     *
     * @param connection The client connection that sent the message.
     * @param message The parsed IRC message.
     */
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

    /**
     * Handles the `PASS` command.
     *
     * Stores the provided password for use during registration authentication.
     * Must be called before NICK/USER registration is complete. Sending PASS
     * after registration results in [IRCReply.ERR_ALREADYREGISTERED].
     *
     * @param connection The client connection.
     * @param message The parsed PASS message. First param must be the password.
     */
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

    /**
     * Supported IRCv3 capabilities advertised via CAP LS.
     *
     * - `sasl` — SASL PLAIN authentication.
     * - `message-tags` — IRCv3 message tags (enables `msgid` on outgoing messages and
     *   `+reply` on incoming messages for threaded conversations).
     * - `msgid` — Indicates that the server assigns a unique ID to each message via the
     *   `msgid` tag (implicitly enabled when `message-tags` is negotiated).
     */
    private val supportedCaps = setOf("sasl", "message-tags", "msgid")

    /**
     * Handles the `CAP` capability negotiation command.
     *
     * Supports subcommands: `LS` (list capabilities), `REQ` (request capability),
     * `ACK`/`NAK` (ack/nack), `END` (end negotiation), and `LIST` (active capabilities).
     * Currently advertises the `sasl` capability.
     *
     * @param connection The client connection.
     * @param message The parsed CAP message.
     */
    private suspend fun handleCap(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        val subcommand = message.params.firstOrNull()?.uppercase()

        when (subcommand) {
            "LS" -> {
                sendMessage(
                    connection,
                    IRCMessage(
                        prefix = serverName,
                        command = "CAP",
                        params = listOf(user.nickname ?: "*", "LS"),
                        trailing = supportedCaps.joinToString(" "),
                    ),
                )
            }

            "REQ" -> {
                val requested = (message.trailing ?: "").split(" ").filter { it.isNotEmpty() }
                val unknown = requested.filter { it !in supportedCaps }
                if (unknown.isNotEmpty()) {
                    sendMessage(
                        connection,
                        IRCMessage(
                            prefix = serverName,
                            command = "CAP",
                            params = listOf(user.nickname ?: "*", "NAK"),
                            trailing = message.trailing,
                        ),
                    )
                } else {
                    user.enabledCaps.addAll(requested)
                    sendMessage(
                        connection,
                        IRCMessage(
                            prefix = serverName,
                            command = "CAP",
                            params = listOf(user.nickname ?: "*", "ACK"),
                            trailing = requested.joinToString(" "),
                        ),
                    )
                }
            }

            "END" -> {
                // Client done with capability negotiation — nothing special needed
            }

            "LIST" -> {
                sendMessage(
                    connection,
                    IRCMessage(
                        prefix = serverName,
                        command = "CAP",
                        params = listOf(user.nickname ?: "*", "LIST"),
                        trailing = user.enabledCaps.joinToString(" "),
                    ),
                )
            }
        }
    }

    private var saslBuffer = ConcurrentHashMap<ClientConnection, String>()

    /**
     * Handles the `AUTHENTICATE` command for SASL PLAIN authentication.
     *
     * Supports multi-chunk base64 payloads (chunks of exactly 400 characters are buffered).
     * On success, sets [IRCUser.authenticated] and [IRCUser.accountName].
     *
     * @param connection The client connection.
     * @param message The parsed AUTHENTICATE message.
     */
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

    /**
     * Handles the `NICK` command to set or change a user's nickname.
     *
     * If the nickname is taken, replies with [IRCReply.ERR_NICKNAMEINUSE].
     * If the user's username is also set, triggers [completeRegistration].
     *
     * @param connection The client connection.
     * @param message The parsed NICK message. First param is the requested nickname.
     */
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

    /**
     * Handles the `USER` command to set the user's username and real name.
     *
     * Triggers [completeRegistration] if [IRCUser.nickname] is already set.
     *
     * @param connection The client connection.
     * @param message The parsed USER message (params: username, mode, unused; trailing: realname).
     */
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

    /**
     * Completes the IRC registration handshake once both NICK and USER have been received.
     *
     * Attempts PASS-based authentication if a password was supplied, then sends the welcome
     * burst (001–004, MOTD) to the client.
     *
     * @param connection The client connection whose registration is being finalised.
     */
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

    /**
     * Handles the `JOIN` command to add the user to one or more channels.
     *
     * Enforces ban list, invite-only (+i), channel key (+k), and user limit (+l) checks.
     * The first user to join a channel automatically becomes its operator.
     *
     * @param connection The client connection.
     * @param message The parsed JOIN message (params: comma-separated channel names[, keys]).
     */
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

    /**
     * Handles the `PART` command to remove the user from a channel.
     *
     * Broadcasts the PART message to remaining channel members and removes the channel
     * from the server if it becomes empty.
     *
     * @param connection The client connection.
     * @param message The parsed PART message (params: channel name; optional trailing: part reason).
     */
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

    /**
     * Handles the `PRIVMSG` command for channel and direct messages.
     *
     * Enforces +n (no external messages) and +m (moderated) channel restrictions.
     * All messages are persisted via [MessageHistoryService] and away notifications
     * are delivered when messaging an absent user.
     *
     * ## Mentions
     * Any `@nickname` pattern in the message text triggers a server [IRCCommand.NOTICE] to the
     * mentioned user (if they are online). The original message is broadcast unchanged so all
     * channel members see the mention in context.
     *
     * ## Replies (IRCv3 message-tags)
     * If the incoming message carries a `+reply=<msgId>` tag, the reply relationship is stored
     * in the database (`replyToId`). The server assigns a `msgid` tag to every broadcast message
     * (using the database row ID) so tag-capable clients can thread conversations.
     * Tags are stripped for clients that have not negotiated the `message-tags` capability.
     *
     * @param connection The client connection.
     * @param message The parsed PRIVMSG message (params: target; trailing: message text).
     */
    private suspend fun handlePrivmsg(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val user = connection.user
        if (!user.isRegistered()) return

        val target = message.params.firstOrNull() ?: return
        val text = message.trailing ?: return

        // Extract +reply tag referencing a parent message ID (IRCv3 client tag)
        val replyToId = message.tags["+reply"]?.toLongOrNull()

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

            // Persist first so we can attach the assigned database ID as msgid
            val msgId =
                xyz.malefic.irc.server.history.MessageHistoryService.logMessage(
                    sender = user.nickname!!,
                    target = target,
                    message = text,
                    messageType = "PRIVMSG",
                    isChannelMessage = true,
                    replyToId = replyToId,
                )

            // Build outgoing tags: msgid from DB row; forward +reply if present
            val outTags =
                buildMap<String, String> {
                    if (msgId != null) put("msgid", msgId.toString())
                    if (replyToId != null) put("+reply", replyToId.toString())
                }

            val privmsg = IRCMessageBuilder.privmsg(user.fullMask(), target, text, outTags)
            broadcastToChannel(channel, privmsg, except = user.nickname)

            // Send mention NOTICEs for every @nick in the text
            mentionRegex.findAll(text).forEach { match ->
                val mentionedNick = match.groupValues[1]
                if (mentionedNick != user.nickname && channel.users.containsKey(mentionedNick)) {
                    users[mentionedNick]?.let { mentionedConn ->
                        sendMessage(
                            mentionedConn,
                            IRCMessageBuilder.mentionNotice(serverName, mentionedNick, target, user.nickname!!, text),
                        )
                    }
                }
            }
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

            val msgId =
                xyz.malefic.irc.server.history.MessageHistoryService.logMessage(
                    sender = user.nickname!!,
                    target = target,
                    message = text,
                    messageType = "PRIVMSG",
                    isChannelMessage = false,
                    replyToId = replyToId,
                )

            val outTags =
                buildMap<String, String> {
                    if (msgId != null) put("msgid", msgId.toString())
                    if (replyToId != null) put("+reply", replyToId.toString())
                }

            val privmsg = IRCMessageBuilder.privmsg(user.fullMask(), target, text, outTags)
            sendMessage(targetConn, privmsg)
        }
    }

    /**
     * Handles the `QUIT` command to gracefully disconnect the client.
     *
     * Delegates to [disconnect] with the optional quit reason from the message trailing.
     *
     * @param connection The client connection.
     * @param message The parsed QUIT message (optional trailing: quit reason).
     */
    private suspend fun handleQuit(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        disconnect(connection, message.trailing ?: "Client quit")
    }

    /**
     * Handles the `PING` command by replying with a matching `PONG`.
     *
     * @param connection The client connection.
     * @param message The parsed PING message (optional params: server token to echo back).
     */
    private suspend fun handlePing(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        val server = message.params.firstOrNull() ?: serverName
        sendMessage(connection, IRCMessageBuilder.pong(serverName, server))
    }

    /**
     * Handles the `TOPIC` command to get or set the topic of a channel.
     *
     * When a trailing text is provided the topic is updated (subject to +t operator-only
     * restriction) and broadcast to all channel members; otherwise the current topic is returned.
     *
     * @param connection The client connection.
     * @param message The parsed TOPIC message (params: channel; optional trailing: new topic text).
     */
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

    /**
     * Handles the `NAMES` command to list the members of a channel.
     *
     * Replies with a 353 name list (prefixed with `@`/`+` for operators/voiced users)
     * followed by a 366 end-of-names reply.
     *
     * @param connection The client connection.
     * @param message The parsed NAMES message (params: channel name).
     */
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

    /**
     * Handles the `LIST` command to enumerate active channels with their topics and user counts.
     *
     * Sends a 321 list-start, one 322 reply per channel, and a 323 list-end reply.
     *
     * @param connection The client connection.
     * @param message The parsed LIST message (params ignored; all channels are returned).
     */
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

    /**
     * Handles the `WHO` command to query users in a channel.
     *
     * Sends a 352 reply for each member of the target channel, followed by a 315
     * end-of-WHO reply.
     *
     * @param connection The client connection.
     * @param message The parsed WHO message (params: target channel name).
     */
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

    /**
     * Handles the `MODE` command, dispatching to [handleUserMode] or [handleChannelMode]
     * based on whether the target begins with `#`.
     *
     * @param connection The client connection.
     * @param message The parsed MODE message (params: target[, modestring[, mode arguments]]).
     */
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

    /**
     * Handles user MODE changes (e.g. `+i`, `+o`, `+w`).
     *
     * Only the target user themselves (or a server operator) may change user modes.
     * Mode changes are applied and echoed back to the client.
     *
     * @param connection The client connection.
     * @param message The parsed MODE message containing the modestring and arguments.
     * @param targetNick The nickname whose modes are being queried or modified.
     */
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

    /**
     * Handles channel MODE changes (e.g. `+o`, `+k`, `+l`, `+b`).
     *
     * Requires the requesting user to be a channel operator for write operations.
     * Mode changes are broadcast to all channel members.
     *
     * @param connection The client connection.
     * @param message The parsed MODE message containing the modestring and arguments.
     * @param channelName The name of the channel whose modes are being queried or modified.
     */
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

    /**
     * Handles the `WHOIS` command to return detailed information about a user.
     *
     * Sends replies for user info (311), server (312), channels (319), idle time (317),
     * and operator status (313) where applicable, terminated by a 318 end-of-WHOIS reply.
     *
     * @param connection The client connection.
     * @param message The parsed WHOIS message (params: target nickname).
     */
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

    /**
     * Handles the `INVITE` command to invite a user to a channel.
     *
     * Requires the inviting user to be a channel operator. Adds the invitee to the
     * channel's invite list and notifies them with a 341 reply.
     *
     * @param connection The client connection.
     * @param message The parsed INVITE message (params: target nickname, channel name).
     */
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

    /**
     * Handles the `KICK` command to forcibly remove a user from a channel.
     *
     * Requires the kicking user to be a channel operator. Broadcasts the KICK message
     * to the channel and removes the target from the channel's user list.
     *
     * @param connection The client connection.
     * @param message The parsed KICK message (params: channel, target nick; optional trailing: reason).
     */
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

    /**
     * Handles the `AWAY` command to set or clear the user's away status.
     *
     * If a trailing message is provided the user is marked away (306); omitting it
     * clears the away status (305). The away message is delivered to other users via PRIVMSG.
     *
     * @param connection The client connection.
     * @param message The parsed AWAY message (optional trailing: away reason).
     */
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

    /**
     * Handles the `OPER` command to grant IRC operator privileges.
     *
     * Validates the supplied name and password against the configured operator credentials.
     * On success the `o` user mode is set and a 381 reply is sent; on failure a 464 reply.
     *
     * @param connection The client connection.
     * @param message The parsed OPER message (params: oper-name, password).
     */
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

    /**
     * Cleans up a client connection, notifying channels and removing the user from the server.
     *
     * @param connection The connection to close.
     * @param reason Human-readable quit reason broadcast to channels the user was in.
     */
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

    /**
     * Sends an IRC message to a single client connection.
     *
     * @param connection The target connection.
     * @param message The [IRCMessage] to send.
     */
    private suspend fun sendMessage(
        connection: ClientConnection,
        message: IRCMessage,
    ) {
        try {
            // Strip IRCv3 tags for clients that have not negotiated message-tags
            val wire = if (connection.user.enabledCaps.contains("message-tags")) message else message.stripTags()
            withContext(Dispatchers.IO) {
                connection.writer.print(wire.toWireFormat())
                connection.writer.flush()
            }
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
        }
    }

    /**
     * Broadcasts an IRC message to all users in a channel, optionally excluding one nick.
     *
     * @param channel The channel to broadcast to.
     * @param message The message to broadcast.
     * @param except Optional nickname to exclude from the broadcast (e.g., the sender).
     */
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
