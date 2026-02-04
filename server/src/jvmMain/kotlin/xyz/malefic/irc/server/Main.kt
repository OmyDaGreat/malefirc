package xyz.malefic.irc.server

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val server = IRCServer(port = 6667, serverName = "malefirc.local")
    println("Starting IRC Server...")
    server.start()
}
