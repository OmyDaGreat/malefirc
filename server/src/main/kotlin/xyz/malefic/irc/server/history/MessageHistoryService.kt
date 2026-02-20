package xyz.malefic.irc.server.history

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountEntity
import xyz.malefic.irc.auth.model.AccountTable

/**
 * Service for managing IRC message history
 */
object MessageHistoryService {
    /**
     * Logs a message to the database.
     *
     * Respects per-user privacy settings: if the sender has disabled message logging
     * (`allowMessageLogging = false` on their account), the message is silently dropped.
     *
     * @param sender Nickname of the sending user.
     * @param target Channel name or recipient nickname.
     * @param message Message body text.
     * @param messageType IRC command type (default `"PRIVMSG"`).
     * @param isChannelMessage `true` if sent to a channel.
     * @param replyToId Database ID of the parent message for threaded replies, or `null`.
     * @return The database ID assigned to the new row, or `null` if the message was not logged.
     */
    fun logMessage(
        sender: String,
        target: String,
        message: String,
        messageType: String = "PRIVMSG",
        isChannelMessage: Boolean = target.startsWith('#'),
        replyToId: Long? = null,
    ): Long? {
        try {
            return transaction {
                // Check if sender has opted out of message logging
                val senderAccount = AccountEntity.find { AccountTable.username eq sender }.firstOrNull()
                if (senderAccount?.allowMessageLogging == false) {
                    return@transaction null
                }

                MessageHistoryEntity
                    .new {
                        this.timestamp = System.currentTimeMillis()
                        this.sender = sender
                        this.target = target
                        this.message = message
                        this.messageType = messageType
                        this.isChannelMessage = isChannelMessage
                        this.replyToId = replyToId
                    }.id.value
            }
        } catch (e: Exception) {
            println("Error logging message: ${e.message}")
            return null
        }
    }

    /**
     * Get message history for a channel
     * @param channelName Channel name (e.g., "#general")
     * @param limit Maximum number of messages to retrieve
     * @param before Optional timestamp to get messages before this time
     */
    fun getChannelHistory(
        channelName: String,
        limit: Int = 100,
        before: Long? = null,
    ): List<MessageHistoryData> =
        transaction {
            val all =
                MessageHistoryEntity
                    .find {
                        (MessageHistoryTable.target eq channelName) and
                            (MessageHistoryTable.isChannelMessage eq true)
                    }.toList()

            val filtered =
                if (before != null) {
                    all.filter { it.timestamp < before }
                } else {
                    all
                }

            filtered
                .sortedByDescending { it.timestamp }
                .take(limit)
                .map { it.toData() }
                .reversed() // Return in chronological order
        }

    /**
     * Get private message history between two users
     * @param user1 First user
     * @param user2 Second user
     * @param limit Maximum number of messages
     * @param before Optional timestamp
     */
    fun getPrivateHistory(
        user1: String,
        user2: String,
        limit: Int = 100,
        before: Long? = null,
    ): List<MessageHistoryData> =
        transaction {
            val all =
                MessageHistoryEntity
                    .find {
                        (
                            ((MessageHistoryTable.sender eq user1) and (MessageHistoryTable.target eq user2)) or
                                ((MessageHistoryTable.sender eq user2) and (MessageHistoryTable.target eq user1))
                        ) and (MessageHistoryTable.isChannelMessage eq false)
                    }.toList()

            val filtered =
                if (before != null) {
                    all.filter { it.timestamp < before }
                } else {
                    all
                }

            filtered
                .sortedByDescending { it.timestamp }
                .take(limit)
                .map { it.toData() }
                .reversed()
        }

    /**
     * Search messages by content
     * @param searchQuery Text to search for
     * @param target Optional channel/user to limit search
     * @param limit Maximum results
     */
    fun searchMessages(
        searchQuery: String,
        target: String? = null,
        limit: Int = 50,
    ): List<MessageHistoryData> =
        transaction {
            val query =
                if (target != null) {
                    MessageHistoryEntity.find {
                        (MessageHistoryTable.target eq target) and
                            (MessageHistoryTable.message like "%$searchQuery%")
                    }
                } else {
                    MessageHistoryEntity.find {
                        MessageHistoryTable.message like "%$searchQuery%"
                    }
                }

            query
                .sortedByDescending { it.timestamp }
                .take(limit)
                .map { it.toData() }
        }

    /**
     * Get messages by sender
     */
    fun getMessagesBySender(
        sender: String,
        limit: Int = 100,
    ): List<MessageHistoryData> =
        transaction {
            MessageHistoryEntity
                .find { MessageHistoryTable.sender eq sender }
                .sortedByDescending { it.timestamp }
                .take(limit)
                .map { it.toData() }
        }

    /**
     * Returns a single message by its database ID, or `null` if not found.
     *
     * @param msgId The database ID of the message.
     */
    fun getMessage(msgId: Long): MessageHistoryData? =
        try {
            transaction { MessageHistoryEntity.findById(msgId)?.toData() }
        } catch (_: Exception) {
            null
        }

    /**
     * Returns all direct replies to the message identified by [msgId], in chronological order.
     *
     * @param msgId The database ID of the parent message.
     * @param limit Maximum number of replies to return.
     */
    fun getReplies(
        msgId: Long,
        limit: Int = 100,
    ): List<MessageHistoryData> =
        try {
            transaction {
                MessageHistoryEntity
                    .find { MessageHistoryTable.replyToId eq msgId }
                    .sortedBy { it.timestamp }
                    .take(limit)
                    .map { it.toData() }
            }
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * Clean up old messages
     * @return Number of messages deleted
     */
    fun cleanupOldMessages(olderThan: Long): Int =
        try {
            transaction {
                val toDelete =
                    MessageHistoryEntity
                        .all()
                        .filter { it.timestamp < olderThan }
                val count = toDelete.size
                toDelete.forEach { it.delete() }
                count
            }
        } catch (e: Exception) {
            println("Error cleaning up messages: ${e.message}")
            0
        }

    /**
     * Clean up messages older than specified days
     */
    fun cleanupMessagesOlderThan(days: Int): Int {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return cleanupOldMessages(cutoffTime)
    }

    /**
     * Get message count for a target
     */
    fun getMessageCount(target: String): Long =
        transaction {
            MessageHistoryEntity
                .find {
                    MessageHistoryTable.target eq target
                }.count()
        }
}

/**
 * Immutable snapshot of a single message history row.
 *
 * @property id Database row identifier.
 * @property timestamp Unix timestamp (ms) when the message was sent.
 * @property sender Nickname of the sending user.
 * @property target Channel name (prefixed with `#`) or recipient nickname.
 * @property message Message body text.
 * @property messageType IRC command type (e.g., `PRIVMSG`, `NOTICE`).
 * @property isChannelMessage `true` if sent to a channel, `false` for private messages.
 * @property replyToId Database ID of the parent message, or `null` for top-level messages.
 */
data class MessageHistoryData(
    val id: Long,
    val timestamp: Long,
    val sender: String,
    val target: String,
    val message: String,
    val messageType: String,
    val isChannelMessage: Boolean,
    val replyToId: Long? = null,
)

/**
 * Extension to convert entity to data class
 */
private fun MessageHistoryEntity.toData() =
    MessageHistoryData(
        id = id.value,
        timestamp = timestamp,
        sender = sender,
        target = target,
        message = message,
        messageType = messageType,
        isChannelMessage = isChannelMessage,
        replyToId = replyToId,
    )
