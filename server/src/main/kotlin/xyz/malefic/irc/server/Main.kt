package xyz.malefic.irc.server

import kotlinx.coroutines.runBlocking
import xyz.malefic.irc.auth.config.DatabaseConfig

fun main() = runBlocking {
    println("Starting IRC Server...")
    
    // Initialize database connection
    try {
        DatabaseConfig.connect()
    } catch (e: Exception) {
        println("Warning: Database connection failed: ${e.message}")
        println("Server will run without authentication support")
    }
    
    val server = IRCServer(port = 6667, serverName = "malefirc.local")
    server.start()
}
