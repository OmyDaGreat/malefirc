package xyz.malefic.irc.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import xyz.malefic.irc.protocol.*

class IRCClient(
    private val host: String,
    private val port: Int = 6667,
    private val nickname: String,
    private val username: String,
    private val realname: String = nickname
) {
    private var socket: Socket? = null
    private var input: ByteReadChannel? = null
    private var output: ByteWriteChannel? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val outgoingMessages = Channel<IRCMessage>(Channel.UNLIMITED)
    private var connected = false
    
    // Callback for received messages
    var onMessage: ((IRCMessage) -> Unit)? = null
    
    suspend fun connect() {
        try {
            socket = aSocket(ActorSelectorManager(Dispatchers.IO))
                .tcp()
                .connect(host, port)
            
            input = socket?.openReadChannel()
            output = socket?.openWriteChannel(autoFlush = true)
            
            connected = true
            
            // Start message handlers
            scope.launch { receiveLoop() }
            scope.launch { sendLoop() }
            
            // Send registration
            register()
            
            println("Connected to $host:$port")
        } catch (e: Exception) {
            println("Failed to connect: ${e.message}")
            connected = false
        }
    }
    
    private suspend fun register() {
        send(IRCMessage(command = IRCCommand.NICK, params = listOf(nickname)))
        send(IRCMessage(
            command = IRCCommand.USER,
            params = listOf(username, "0", "*"),
            trailing = realname
        ))
    }
    
    private suspend fun receiveLoop() {
        try {
            while (connected) {
                val line = input?.readUTF8Line() ?: break
                val message = IRCMessage.parse(line) ?: continue
                
                // Handle automatic PING/PONG
                if (message.command == IRCCommand.PING) {
                    val server = message.params.firstOrNull() ?: message.trailing ?: ""
                    send(IRCMessage(command = IRCCommand.PONG, params = listOf(server)))
                }
                
                // Call message handler
                onMessage?.invoke(message)
            }
        } catch (e: Exception) {
            if (connected) {
                println("Connection lost: ${e.message}")
                connected = false
            }
        }
    }
    
    private suspend fun sendLoop() {
        try {
            for (message in outgoingMessages) {
                output?.writeStringUtf8(message.toWireFormat())
            }
        } catch (e: Exception) {
            if (connected) {
                println("Error sending message: ${e.message}")
            }
        }
    }
    
    suspend fun send(message: IRCMessage) {
        if (connected) {
            outgoingMessages.send(message)
        }
    }
    
    suspend fun joinChannel(channel: String) {
        send(IRCMessage(command = IRCCommand.JOIN, params = listOf(channel)))
    }
    
    suspend fun partChannel(channel: String, reason: String? = null) {
        send(IRCMessage(command = IRCCommand.PART, params = listOf(channel), trailing = reason))
    }
    
    suspend fun sendMessage(target: String, text: String) {
        send(IRCMessage(command = IRCCommand.PRIVMSG, params = listOf(target), trailing = text))
    }
    
    suspend fun setTopic(channel: String, topic: String) {
        send(IRCMessage(command = IRCCommand.TOPIC, params = listOf(channel), trailing = topic))
    }
    
    suspend fun getTopic(channel: String) {
        send(IRCMessage(command = IRCCommand.TOPIC, params = listOf(channel)))
    }
    
    suspend fun listChannels() {
        send(IRCMessage(command = IRCCommand.LIST))
    }
    
    suspend fun getNames(channel: String) {
        send(IRCMessage(command = IRCCommand.NAMES, params = listOf(channel)))
    }
    
    suspend fun quit(reason: String = "Client quit") {
        if (connected) {
            send(IRCMessage(command = IRCCommand.QUIT, trailing = reason))
            delay(100) // Give time for message to send
            disconnect()
        }
    }
    
    fun disconnect() {
        connected = false
        scope.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    fun isConnected() = connected
}
