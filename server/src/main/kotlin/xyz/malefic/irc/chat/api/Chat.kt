package xyz.malefic.irc.chat.api

import com.varabyte.kobweb.api.stream.ApiStream

/**
 * Kobweb WebSocket stream endpoint for real-time chat.
 *
 * Broadcasts any received text message to all connected clients except the sender.
 * Used by the web client for live message exchange.
 */
val chat =
    ApiStream { ctx ->
        ctx.stream.broadcast(ctx.text) { it != ctx.stream.id }
    }
