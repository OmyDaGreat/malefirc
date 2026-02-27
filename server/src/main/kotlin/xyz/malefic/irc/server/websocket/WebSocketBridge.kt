package xyz.malefic.irc.server.websocket

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * WebSocket-to-IRC protocol bridge.
 *
 * Starts an embedded Ktor HTTP server that accepts WebSocket connections and transparently
 * relays IRC protocol messages to the local IRC server over a plain TCP connection.  Each
 * WebSocket client gets its own dedicated TCP connection to the IRC server, so standard IRC
 * protocol negotiation (NICK, USER, JOIN, PRIVMSG, …) takes place exactly as it would with
 * a native TCP client — no proprietary framing is needed.
 *
 * ## Protocol
 * WebSocket clients send and receive raw IRC protocol lines (UTF-8 text frames).
 * Every frame from the client to the bridge **must** be a complete IRC line **without** the
 * trailing `\r\n`; the bridge appends `\r\n` before forwarding to the IRC server.
 * Every line received from the IRC server is forwarded to the WebSocket client as a single
 * text frame, with the trailing `\r\n` stripped.
 *
 * ## Endpoint
 * `ws://<host>:<wsPort>/irc`
 *
 * ## Configuration via environment variables
 * | Variable | Default | Description |
 * |---|---|---|
 * | `IRC_WS_PORT` | `6680` | Port the WebSocket bridge listens on |
 * | `IRC_WS_HOST` | `0.0.0.0` | Host/interface the bridge binds to |
 * | `IRC_HOST` | `127.0.0.1` | IRC server host the bridge connects to |
 * | `IRC_PORT` | `6667` | IRC server port the bridge connects to |
 *
 * @param wsPort Port for the WebSocket server (default from `IRC_WS_PORT` or 6680).
 * @param wsHost Host/interface to bind to (default from `IRC_WS_HOST` or `0.0.0.0`).
 * @param ircHost IRC server host to relay to (default from `IRC_HOST` or `127.0.0.1`).
 * @param ircPort IRC server port to relay to (default from `IRC_PORT` or 6667).
 */
class WebSocketBridge(
    private val wsPort: Int = System.getenv("IRC_WS_PORT")?.toIntOrNull() ?: 6680,
    private val wsHost: String = System.getenv("IRC_WS_HOST") ?: "0.0.0.0",
    private val ircHost: String = System.getenv("IRC_HOST") ?: "127.0.0.1",
    private val ircPort: Int = System.getenv("IRC_PORT")?.toIntOrNull() ?: 6667,
) {
    /**
     * Starts the WebSocket bridge server.
     *
     * Launches the embedded Ktor server in a non-blocking manner (does not wait for
     * the server to stop).  The server continues to run until the JVM exits.
     */
    fun start() {
        embeddedServer(Netty, port = wsPort, host = wsHost) {
            install(WebSockets)
            routing {
                webSocket("/irc") {
                    relay()
                }
            }
        }.start(wait = false)

        println("WebSocket bridge started on port $wsPort (ws://$wsHost:$wsPort/irc)")
    }

    /**
     * Relays messages between a WebSocket session and a fresh TCP connection to the IRC server.
     *
     * Two coroutines run concurrently inside the session:
     * - **IRC → WS**: Reads lines from the IRC TCP socket and sends them as WebSocket text frames.
     * - **WS → IRC**: Reads incoming WebSocket text frames and writes them as IRC lines to the TCP socket.
     *
     * Both directions are closed cleanly when either end disconnects.
     */
    private suspend fun DefaultWebSocketServerSession.relay() {
        val tcpSocket =
            runCatching { withContext(Dispatchers.IO) { Socket(ircHost, ircPort) } }.getOrElse { e ->
                println("WebSocket bridge: could not connect to IRC server – ${e.message}")
                close()
                return
            }

        val reader = BufferedReader(InputStreamReader(tcpSocket.inputStream, Charsets.UTF_8))
        val writer = PrintWriter(tcpSocket.outputStream, false, Charsets.UTF_8)

        // IRC → WebSocket relay
        val ircToWs =
            launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        outgoing.send(Frame.Text(line))
                    }
                } catch (_: Exception) {
                    // Connection closed from IRC side
                } finally {
                    close()
                }
            }

        // WebSocket → IRC relay
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    withContext(Dispatchers.IO) {
                        writer.print("$text\r\n")
                        writer.flush()
                    }
                }
            }
        } catch (_: Exception) {
            // WebSocket closed from client side
        } finally {
            ircToWs.cancelAndJoin()
            runCatching { withContext(Dispatchers.IO) { tcpSocket.close() } }
        }
    }
}
