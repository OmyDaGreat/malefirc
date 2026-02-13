package xyz.malefic.irc.server.history

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Service for managing IRC message history
 */
object MessageHistoryService {
    /**
     * Log a message to the database
     */
    fun logMessage(
        sender: String,
        target: String,
        message: String,
        messageType: String = "PRIVMSG",
        isChannelMessage: Boolean = target.startsWith('#')
    ) {
        try {
            transaction {
                MessageHistoryEntity.new {
                    this.timestamp = System.currentTimeMillis()
                    this.sender = sender
                    this.target = target
                    this.message = message
                    this.messageType = messageType
                    this.isChannelMessage = isChannelMessage
                }
            }
        } catch (e: Exception) {
            println("Error logging message: ${e.message}")
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
        before: Long? = null
    ): List<MessageHistoryData> {
        return transaction {
            val all = MessageHistoryEntity
                .find { 
                    (MessageHistoryTable.target eq channelName) and
                    (MessageHistoryTable.isChannelMessage eq true)
                }
                .toList()
            
            val filtered = if (before != null) {
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
        before: Long? = null
    ): List<MessageHistoryData> {
        return transaction {
            val all = MessageHistoryEntity.find {
                (
                    ((MessageHistoryTable.sender eq user1) and (MessageHistoryTable.target eq user2)) or
                    ((MessageHistoryTable.sender eq user2) and (MessageHistoryTable.target eq user1))
                ) and (MessageHistoryTable.isChannelMessage eq false)
            }.toList()
            
            val filtered = if (before != null) {
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
        limit: Int = 50
    ): List<MessageHistoryData> {
        return transaction {
            val query = if (target != null) {
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
    }
    
    /**
     * Get messages by sender
     */
    fun getMessagesBySender(
        sender: String,
        limit: Int = 100
    ): List<MessageHistoryData> {
        return transaction {
            MessageHistoryEntity
                .find { MessageHistoryTable.sender eq sender }
                .sortedByDescending { it.timestamp }
                .take(limit)
                .map { it.toData() }
        }
    }
    
    /**
     * Clean up old messages
     * @param olderThan Delete messages older than this timestamp
     * @return Number of messages deleted
     */
    fun cleanupOldMessages(olderThan: Long): Int {
        return try {
            transaction {
                val toDelete = MessageHistoryEntity.all()
                    .filter { it.timestamp < olderThan }
                val count = toDelete.size
                toDelete.forEach { it.delete() }
                count
            }
        } catch (e: Exception) {
            println("Error cleaning up messages: ${e.message}")
            0
        }
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
    fun getMessageCount(target: String): Long {
        return transaction {
            MessageHistoryEntity.find {
                MessageHistoryTable.target eq target
            }.count()
        }
    }
}

/**
 * Data class for message history
 */
data class MessageHistoryData(
    val id: Long,
    val timestamp: Long,
    val sender: String,
    val target: String,
    val message: String,
    val messageType: String,
    val isChannelMessage: Boolean
)

/**
 * Extension to convert entity to data class
 */
private fun MessageHistoryEntity.toData() = MessageHistoryData(
    id = id.value,
    timestamp = timestamp,
    sender = sender,
    target = target,
    message = message,
    messageType = messageType,
    isChannelMessage = isChannelMessage
)
