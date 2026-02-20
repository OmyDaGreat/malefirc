package xyz.malefic.irc.server.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountEntity
import xyz.malefic.irc.auth.model.AccountTable
import xyz.malefic.irc.server.history.MessageHistoryService
import xyz.malefic.irc.server.history.MessageHistoryData

/**
 * REST-oriented API facade for querying IRC message history.
 *
 * All methods return a JSON-encoded [HistoryResponse] or [CountResponse] string.
 * Every call requires a `requestingUser` parameter; unauthenticated requests return
 * `{ success: false, error: "Authentication required" }`.
 *
 * Privacy is enforced by filtering out messages whose senders have set
 * `allow_history_access = false` in the [AccountTable].
 *
 * @see MessageHistoryService for the underlying database queries
 */
object MessageHistoryAPI {
    
    /**
     * Check if a user has allowed history access
     * @param username User to check
     * @return true if user allows history access (or doesn't exist in auth system)
     */
    private fun checkHistoryAccessAllowed(username: String): Boolean {
        return try {
            transaction {
                val account = AccountEntity.find { AccountTable.username eq username }.firstOrNull()
                account?.allowHistoryAccess ?: true // Default to true if not in auth system
            }
        } catch (e: Exception) {
            println("Error checking history access: ${e.message}")
            true // Fail open to avoid breaking existing functionality
        }
    }
    
    /**
     * Get channel message history
     * @param requestingUser User making the request (for authentication)
     * @param channelName Channel to get history for
     * @param limit Maximum number of messages
     * @param before Optional timestamp filter
     */
    fun getChannelHistory(
        requestingUser: String?,
        channelName: String,
        limit: Int = 100,
        before: Long? = null
    ): String {
        // Require authentication for history access
        if (requestingUser == null) {
            return Json.encodeToString(
                HistoryResponse.serializer(),
                HistoryResponse(success = false, messages = emptyList(), error = "Authentication required")
            )
        }
        
        val history = MessageHistoryService.getChannelHistory(channelName, limit, before)
        
        // Filter out messages from users who have disabled history access
        val filteredHistory = history.filter { checkHistoryAccessAllowed(it.sender) }
        
        val response = HistoryResponse(
            success = true,
            messages = filteredHistory.map { it.toApiMessage() }
        )
        return Json.encodeToString(HistoryResponse.serializer(), response)
    }
    
    /**
     * Get private message history between two users
     * @param requestingUser User making the request (must be one of the participants)
     * @param user1 First user
     * @param user2 Second user
     * @param limit Maximum number of messages
     * @param before Optional timestamp filter
     */
    fun getPrivateHistory(
        requestingUser: String?,
        user1: String,
        user2: String,
        limit: Int = 100,
        before: Long? = null
    ): String {
        // Require authentication
        if (requestingUser == null) {
            return Json.encodeToString(
                HistoryResponse.serializer(),
                HistoryResponse(success = false, messages = emptyList(), error = "Authentication required")
            )
        }
        
        // User can only access their own private messages
        if (requestingUser != user1 && requestingUser != user2) {
            return Json.encodeToString(
                HistoryResponse.serializer(),
                HistoryResponse(success = false, messages = emptyList(), error = "Access denied")
            )
        }
        
        // Check if both users allow history access
        if (!checkHistoryAccessAllowed(user1) || !checkHistoryAccessAllowed(user2)) {
            return Json.encodeToString(
                HistoryResponse.serializer(),
                HistoryResponse(success = false, messages = emptyList(), error = "History access disabled by user")
            )
        }
        
        val history = MessageHistoryService.getPrivateHistory(user1, user2, limit, before)
        val response = HistoryResponse(
            success = true,
            messages = history.map { it.toApiMessage() }
        )
        return Json.encodeToString(HistoryResponse.serializer(), response)
    }
    
    /**
     * Search messages
     * @param requestingUser User making the request (for authentication)
     * @param query Search query
     * @param target Optional target to limit search
     * @param limit Maximum results
     */
    fun searchMessages(
        requestingUser: String?,
        query: String,
        target: String? = null,
        limit: Int = 50
    ): String {
        // Require authentication
        if (requestingUser == null) {
            return Json.encodeToString(
                HistoryResponse.serializer(),
                HistoryResponse(success = false, messages = emptyList(), error = "Authentication required")
            )
        }
        
        val results = MessageHistoryService.searchMessages(query, target, limit)
        
        // Filter based on privacy settings
        val filteredResults = results.filter { checkHistoryAccessAllowed(it.sender) }
        
        val response = HistoryResponse(
            success = true,
            messages = filteredResults.map { it.toApiMessage() }
        )
        return Json.encodeToString(HistoryResponse.serializer(), response)
    }
    
    /**
     * Get messages by sender
     * @param requestingUser User making the request
     * @param sender Sender to get messages for
     * @param limit Maximum messages
     */
    fun getMessagesBySender(
        requestingUser: String?,
        sender: String,
        limit: Int = 100
    ): String {
        // Require authentication
        if (requestingUser == null) {
            return Json.encodeToString(
                HistoryResponse.serializer(),
                HistoryResponse(success = false, messages = emptyList(), error = "Authentication required")
            )
        }
        
        // Users can only access their own message history this way
        if (requestingUser != sender) {
            return Json.encodeToString(
                HistoryResponse.serializer(),
                HistoryResponse(success = false, messages = emptyList(), error = "Access denied")
            )
        }
        
        // Check privacy settings
        if (!checkHistoryAccessAllowed(sender)) {
            return Json.encodeToString(
                HistoryResponse.serializer(),
                HistoryResponse(success = false, messages = emptyList(), error = "History access disabled")
            )
        }
        
        val messages = MessageHistoryService.getMessagesBySender(sender, limit)
        val response = HistoryResponse(
            success = true,
            messages = messages.map { it.toApiMessage() }
        )
        return Json.encodeToString(HistoryResponse.serializer(), response)
    }
    
    /**
     * Get message count for a target
     * @param requestingUser User making the request
     * @param target Target to count messages for
     */
    fun getMessageCount(
        requestingUser: String?,
        target: String
    ): String {
        // Require authentication
        if (requestingUser == null) {
            return Json.encodeToString(
                CountResponse.serializer(),
                CountResponse(success = false, count = 0, error = "Authentication required")
            )
        }
        
        val count = MessageHistoryService.getMessageCount(target)
        val response = CountResponse(
            success = true,
            count = count
        )
        return Json.encodeToString(CountResponse.serializer(), response)
    }
}

/**
 * JSON response envelope for history queries.
 *
 * @property success Whether the request succeeded.
 * @property messages List of messages returned (empty on failure).
 * @property error Human-readable error message when [success] is `false`.
 */
@Serializable
data class HistoryResponse(
    val success: Boolean,
    val messages: List<ApiMessage>,
    val error: String? = null
)

/**
 * A single IRC message in API format, safe for JSON serialisation.
 *
 * @property id Database row ID.
 * @property timestamp Unix timestamp (ms).
 * @property sender Nickname of the sender.
 * @property target Channel or recipient nickname.
 * @property message Message body.
 * @property messageType IRC command (`PRIVMSG`, `NOTICE`, etc.).
 * @property isChannelMessage `true` for channel messages, `false` for private messages.
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
 * JSON response for message count queries.
 *
 * @property success Whether the request succeeded.
 * @property count Number of messages matching the query.
 * @property error Human-readable error message when [success] is `false`.
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
