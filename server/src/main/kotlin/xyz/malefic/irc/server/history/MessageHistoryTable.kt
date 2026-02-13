package xyz.malefic.irc.server.history

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * Table for storing IRC message history
 */
object MessageHistoryTable : LongIdTable("message_history") {
    val timestamp = long("timestamp")
    val sender = varchar("sender", 50)
    val target = varchar("target", 50) // Channel name or recipient nickname
    val message = text("message")
    val messageType = varchar("message_type", 20).default("PRIVMSG") // PRIVMSG, NOTICE, etc.
    val isChannelMessage = bool("is_channel_message")
    
    // Indexes for common queries
    init {
        index(false, target, timestamp)
        index(false, sender, timestamp)
    }
}

/**
 * Entity for message history entries
 */
class MessageHistoryEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<MessageHistoryEntity>(MessageHistoryTable)
    
    var timestamp by MessageHistoryTable.timestamp
    var sender by MessageHistoryTable.sender
    var target by MessageHistoryTable.target
    var message by MessageHistoryTable.message
    var messageType by MessageHistoryTable.messageType
    var isChannelMessage by MessageHistoryTable.isChannelMessage
}
