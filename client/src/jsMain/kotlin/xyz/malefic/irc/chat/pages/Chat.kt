package xyz.malefic.irc.chat.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.window
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.WebSocket
import xyz.malefic.irc.auth.components.sections.LoggedOutMessage
import xyz.malefic.irc.auth.model.auth.LoginState
import xyz.malefic.irc.core.G
import xyz.malefic.irc.core.components.layouts.PageLayout
import xyz.malefic.irc.core.components.sections.CenteredColumnContent
import xyz.malefic.irc.core.components.widgets.TextButton
import xyz.malefic.irc.protocol.IRCMessage

/** WebSocket bridge port — matches [xyz.malefic.irc.server.websocket.WebSocketBridge] default. */
private const val WS_PORT = 6680

/** Default IRC channel to join and send messages to after registration. */
private const val DEFAULT_CHANNEL = "#general"

val ChatBoxStyle =
    CssStyle.base {
        Modifier
            .padding(5.px)
            .borderRadius(5.px)
            .border { style(LineStyle.Solid) }
            .overflow { y(Overflow.Auto) }
    }

/**
 * A single chat line displayed in the message list.
 *
 * @property nick Sender's IRC nickname.
 * @property text Message body.
 */
private data class ChatLine(val nick: String, val text: String) {
    override fun toString() = "$nick: $text"
}

/**
 * Web chat page backed by the IRC WebSocket bridge.
 *
 * Connects to `ws://<host>:[WS_PORT]/irc`, registers the logged-in account as an IRC user,
 * joins [DEFAULT_CHANNEL], and renders a scrollable message list with a text input.
 * Handles `PING` keepalive and `PRIVMSG` display automatically.
 *
 * Requires the user to be logged in ([LoginState.LoggedIn]); otherwise shows [LoggedOutMessage].
 */
@Page
@Composable
fun ChatPage() {
    PageLayout("Chat") {
        val account =
            (LoginState.current as? LoginState.LoggedIn)?.account ?: run {
                LoggedOutMessage()
                return@PageLayout
            }

        val lines = remember { mutableStateListOf<ChatLine>() }
        val wsState = remember { mutableStateOf<WebSocket?>(null) }

        // Open a fresh WebSocket connection whenever the logged-in account changes.
        DisposableEffect(account.username) {
            val ws = WebSocket("ws://${window.location.hostname}:$WS_PORT/irc")
            wsState.value = ws

            ws.onopen = {
                ws.send("NICK ${account.username}")
                ws.send("USER ${account.username} 0 * :Web IRC User")
                Unit
            }

            ws.onmessage = { event ->
                val line = event.data.toString()
                when {
                    // Keep-alive: reply to PING immediately
                    line.startsWith("PING ") -> ws.send("PONG ${line.drop(5)}")
                    else -> {
                        val msg = IRCMessage.parse(line)
                        when (msg?.command) {
                            // Registration complete — join the default channel
                            "001" -> ws.send("JOIN $DEFAULT_CHANNEL")
                            // Display channel and private messages
                            "PRIVMSG" -> {
                                val nick = msg.prefix?.substringBefore('!')
                                val text = msg.trailing
                                if (nick != null && text != null) {
                                    lines.add(ChatLine(nick, text))
                                }
                            }
                        }
                    }
                }
                Unit
            }

            onDispose {
                ws.close()
                wsState.value = null
            }
        }

        CenteredColumnContent {
            Column(
                ChatBoxStyle
                    .toModifier()
                    .height(80.percent)
                    .width(G.Ui.Width.Large)
                    .fontSize(G.Ui.Text.MediumSmall),
            ) {
                lines.forEach { line ->
                    Text(line.toString())
                    Br()
                }
            }
            Box(Modifier.width(G.Ui.Width.Large).height(60.px)) {
                var message by remember { mutableStateOf("") }

                fun sendMessage() {
                    if (message.isBlank()) return
                    wsState.value?.send("PRIVMSG $DEFAULT_CHANNEL :${message.trim()}")
                    message = ""
                }

                TextInput(
                    message,
                    { message = it },
                    Modifier.width(70.percent).align(Alignment.BottomStart),
                    ref = ref { it.focus() },
                    onCommit = ::sendMessage,
                )
                TextButton(
                    "Send",
                    Modifier.width(20.percent).align(Alignment.BottomEnd),
                    enabled = message.isNotBlank(),
                    onClick = ::sendMessage,
                )
            }
        }
    }
}

