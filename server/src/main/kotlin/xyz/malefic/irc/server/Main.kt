package xyz.malefic.irc.server

import kotlinx.coroutines.runBlocking
import xyz.malefic.irc.auth.config.DatabaseConfig
import xyz.malefic.irc.server.tls.TLSConfig
import xyz.malefic.irc.server.websocket.WebSocketBridge

/**
 * Entry point for the Malefirc IRC server.
 *
 * Initialises the database connection, starts the [WebSocketBridge], and then starts the
 * [IRCServer].  All configuration is read from environment variables.
 *
 * ## Services started
 * - **IRC server** — plain TCP on `IRC_PORT` (default 6667)
 * - **TLS listener** — when `IRC_TLS_ENABLED=true`, on `IRC_TLS_PORT` (default 6697)
 * - **WebSocket bridge** — always started, on `IRC_WS_PORT` (default 6680)
 *
 * See [IRCServer], [TLSConfig], and [WebSocketBridge] for the full list of environment variables.
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

        // Start WebSocket bridge (non-blocking; relays WS clients to this IRC server)
        val wsEnabled = System.getenv("IRC_WS_ENABLED")?.lowercase() != "false"
        if (wsEnabled) {
            WebSocketBridge().start()
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
