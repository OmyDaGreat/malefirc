package xyz.malefic.irc.client

import kotlinx.coroutines.*
import xyz.malefic.irc.protocol.IRCCommand
import xyz.malefic.irc.protocol.IRCMessage
import java.util.*

class IRCClientUI(
    private val client: IRCClient
) {
    private var currentChannel: String? = null
    private val channels = mutableSetOf<String>()
    
    suspend fun run() {
        client.onMessage = ::handleMessage
        
        println("=== Malefirc IRC Client ===")
        println("Connecting...")
        
        client.connect()
        
        if (!client.isConnected()) {
            println("Failed to connect to server")
            return
        }
        
        println("\nCommands:")
        println("  /join <#channel>    - Join a channel")
        println("  /part [reason]      - Leave current channel")
        println("  /msg <target> <msg> - Send private message")
        println("  /topic [new topic]  - Get or set channel topic")
        println("  /list               - List all channels")
        println("  /names              - List users in current channel")
        println("  /quit [reason]      - Disconnect")
        println("  /help               - Show this help")
        println()
        
        val scanner = Scanner(System.`in`)
        
        while (client.isConnected()) {
            try {
                val input = withContext(Dispatchers.IO) {
                    if (scanner.hasNextLine()) scanner.nextLine() else null
                } ?: break
                
                if (input.startsWith("/")) {
                    handleCommand(input)
                } else if (currentChannel != null) {
                    client.sendMessage(currentChannel!!, input)
                } else {
                    println("Not in a channel. Use /join #channelname to join a channel")
                }
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
        
        client.disconnect()
        println("Disconnected")
    }
    
    private suspend fun handleCommand(input: String) {
        val parts = input.substring(1).split(" ", limit = 2)
        val command = parts[0].lowercase()
        val args = parts.getOrNull(1)
        
        when (command) {
            "join" -> {
                val channel = args?.trim()
                if (channel.isNullOrBlank()) {
                    println("Usage: /join <#channel>")
                } else {
                    val channelName = if (channel.startsWith('#')) channel else "#$channel"
                    client.joinChannel(channelName)
                    currentChannel = channelName
                    channels.add(channelName)
                }
            }
            "part" -> {
                if (currentChannel == null) {
                    println("Not in a channel")
                } else {
                    client.partChannel(currentChannel!!, args)
                    channels.remove(currentChannel)
                    currentChannel = channels.firstOrNull()
                }
            }
            "msg" -> {
                val msgParts = args?.split(" ", limit = 2)
                if (msgParts == null || msgParts.size < 2) {
                    println("Usage: /msg <target> <message>")
                } else {
                    client.sendMessage(msgParts[0], msgParts[1])
                }
            }
            "topic" -> {
                if (currentChannel == null) {
                    println("Not in a channel")
                } else if (args.isNullOrBlank()) {
                    client.getTopic(currentChannel!!)
                } else {
                    client.setTopic(currentChannel!!, args)
                }
            }
            "list" -> {
                client.listChannels()
            }
            "names" -> {
                if (currentChannel == null) {
                    println("Not in a channel")
                } else {
                    client.getNames(currentChannel!!)
                }
            }
            "quit" -> {
                client.quit(args ?: "Client quit")
            }
            "help" -> {
                println("\nCommands:")
                println("  /join <#channel>    - Join a channel")
                println("  /part [reason]      - Leave current channel")
                println("  /msg <target> <msg> - Send private message")
                println("  /topic [new topic]  - Get or set channel topic")
                println("  /list               - List all channels")
                println("  /names              - List users in current channel")
                println("  /quit [reason]      - Disconnect")
                println("  /help               - Show this help")
            }
            else -> {
                println("Unknown command: /$command")
            }
        }
    }
    
    private fun handleMessage(message: IRCMessage) {
        when (message.command) {
            IRCCommand.PRIVMSG -> {
                val from = message.prefix?.substringBefore('!') ?: "?"
                val target = message.params.firstOrNull() ?: "?"
                val text = message.trailing ?: ""
                
                if (target == currentChannel) {
                    println("[$target] <$from> $text")
                } else if (target.startsWith('#')) {
                    println("[$target] <$from> $text")
                } else {
                    println("[PRIVATE] <$from> $text")
                }
            }
            IRCCommand.JOIN -> {
                val user = message.prefix?.substringBefore('!') ?: "?"
                val channel = message.params.firstOrNull() ?: "?"
                println("*** $user has joined $channel")
            }
            IRCCommand.PART -> {
                val user = message.prefix?.substringBefore('!') ?: "?"
                val channel = message.params.firstOrNull() ?: "?"
                val reason = message.trailing
                println("*** $user has left $channel${reason?.let { " ($it)" } ?: ""}")
            }
            IRCCommand.QUIT -> {
                val user = message.prefix?.substringBefore('!') ?: "?"
                val reason = message.trailing ?: "Client quit"
                println("*** $user has quit ($reason)")
            }
            IRCCommand.TOPIC -> {
                val user = message.prefix?.substringBefore('!') ?: "?"
                val channel = message.params.firstOrNull() ?: "?"
                val topic = message.trailing ?: ""
                println("*** $user changed topic of $channel to: $topic")
            }
            "001", "002", "003", "004", "005" -> {
                // Welcome messages
                println("*** ${message.trailing}")
            }
            "332" -> {
                // Topic
                val channel = message.params.getOrNull(1) ?: "?"
                val topic = message.trailing ?: ""
                println("*** Topic for $channel: $topic")
            }
            "353" -> {
                // Names reply
                val channel = message.params.getOrNull(2) ?: "?"
                val names = message.trailing ?: ""
                println("*** Users in $channel: $names")
            }
            "322" -> {
                // List reply
                val channel = message.params.getOrNull(1) ?: "?"
                val userCount = message.params.getOrNull(2) ?: "?"
                val topic = message.trailing ?: ""
                println("  $channel ($userCount users) - $topic")
            }
            "323" -> {
                // End of list
                println("*** ${message.trailing}")
            }
            else -> {
                // Debug: print all other messages
                if (message.trailing != null) {
                    println("*** ${message.trailing}")
                }
            }
        }
    }
}

fun main() = runBlocking {
    println("Enter server hostname (default: localhost):")
    val host = readlnOrNull()?.ifBlank { "localhost" } ?: "localhost"
    
    println("Enter server port (default: 6667):")
    val port = readlnOrNull()?.toIntOrNull() ?: 6667
    
    println("Enter your nickname:")
    val nickname = readlnOrNull()?.ifBlank { null } ?: run {
        println("Nickname is required")
        return@runBlocking
    }
    
    println("Enter your username (default: $nickname):")
    val username = readlnOrNull()?.ifBlank { nickname } ?: nickname
    
    val client = IRCClient(host, port, nickname, username)
    val ui = IRCClientUI(client)
    
    ui.run()
}
