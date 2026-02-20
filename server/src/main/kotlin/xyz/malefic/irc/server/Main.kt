package xyz.malefic.irc.server

import kotlinx.coroutines.runBlocking
import xyz.malefic.irc.auth.config.DatabaseConfig
import xyz.malefic.irc.server.tls.TLSConfig

/**
 * Entry point for the Malefirc IRC server.
 *
 * Initialises the database connection and starts the [IRCServer].
 * TLS support is activated when the following environment variables are set:
 * - `IRC_TLS_ENABLED=true` — starts a dedicated TLS listener on [TLSConfig.tlsPort].
 *
 * The server reads all configuration from environment variables.
 * See [IRCServer] and [TLSConfig] for the full list.
 */
fun main() =
    runBlocking {
        println("Starting IRC Server...")

        // Initialise database connection
        try {
            DatabaseConfig.connect()
        } catch (e: Exception) {
            println("Warning: Database connection failed: ${e.message}")
            println("Server will run without authentication support")
        }

        val server =
            IRCServer(
                port = System.getenv("IRC_PORT")?.toIntOrNull() ?: 6667,
                serverName = System.getenv("IRC_SERVER_NAME") ?: "malefirc.local",
                tlsEnabled = TLSConfig.tlsEnabled,
                tlsPort = TLSConfig.tlsPort,
            )

        if (TLSConfig.tlsEnabled) {
            println("TLS enabled on port ${TLSConfig.tlsPort}")
        }

        server.start()
    }
