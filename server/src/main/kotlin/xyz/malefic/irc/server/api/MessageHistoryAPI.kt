package xyz.malefic.irc.server.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.malefic.irc.server.history.MessageHistoryService
import xyz.malefic.irc.server.history.MessageHistoryData

/**
 * API endpoint for retrieving message history
 * This is designed to be used with Kobweb API or similar web framework
 */
object MessageHistoryAPI {
    
    /**
     * Get channel message history
     */
    fun getChannelHistory(
        channelName: String,
        limit: Int = 100,
        before: Long? = null
    ): String {
        val history = MessageHistoryService.getChannelHistory(channelName, limit, before)
        val response = HistoryResponse(
            success = true,
            messages = history.map { it.toApiMessage() }
        )
        return Json.encodeToString(HistoryResponse.serializer(), response)
    }
    
    /**
     * Get private message history between two users
     */
    fun getPrivateHistory(
        user1: String,
        user2: String,
        limit: Int = 100,
        before: Long? = null
    ): String {
        val history = MessageHistoryService.getPrivateHistory(user1, user2, limit, before)
        val response = HistoryResponse(
            success = true,
            messages = history.map { it.toApiMessage() }
        )
        return Json.encodeToString(HistoryResponse.serializer(), response)
    }
    
    /**
     * Search messages
     */
    fun searchMessages(
        query: String,
        target: String? = null,
        limit: Int = 50
    ): String {
        val results = MessageHistoryService.searchMessages(query, target, limit)
        val response = HistoryResponse(
            success = true,
            messages = results.map { it.toApiMessage() }
        )
        return Json.encodeToString(HistoryResponse.serializer(), response)
    }
    
    /**
     * Get messages by sender
     */
    fun getMessagesBySender(
        sender: String,
        limit: Int = 100
    ): String {
        val messages = MessageHistoryService.getMessagesBySender(sender, limit)
        val response = HistoryResponse(
            success = true,
            messages = messages.map { it.toApiMessage() }
        )
        return Json.encodeToString(HistoryResponse.serializer(), response)
    }
    
    /**
     * Get message count for a target
     */
    fun getMessageCount(target: String): String {
        val count = MessageHistoryService.getMessageCount(target)
        val response = CountResponse(
            success = true,
            count = count
        )
        return Json.encodeToString(CountResponse.serializer(), response)
    }
}

/**
 * API response for message history
 */
@Serializable
data class HistoryResponse(
    val success: Boolean,
    val messages: List<ApiMessage>,
    val error: String? = null
)

/**
 * API message format
 */
@Serializable
data class ApiMessage(
    val id: Long,
    val timestamp: Long,
    val sender: String,
    val target: String,
    val message: String,
    val messageType: String,
    val isChannelMessage: Boolean
)

/**
 * Count response
 */
@Serializable
data class CountResponse(
    val success: Boolean,
    val count: Long,
    val error: String? = null
)

/**
 * Extension to convert message history data to API format
 */
private fun MessageHistoryData.toApiMessage() = ApiMessage(
    id = id,
    timestamp = timestamp,
    sender = sender,
    target = target,
    message = message,
    messageType = messageType,
    isChannelMessage = isChannelMessage
)
