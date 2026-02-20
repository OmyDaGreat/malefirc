package xyz.malefic.irc.server.history

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import xyz.malefic.irc.auth.model.AccountTable

/**
 * Exposed ORM table definition for IRC message history.
 *
 * Records every PRIVMSG (and other message types) sent through the server, subject to
 * per-user privacy settings ([AccountTable.allowMessageLogging]).
 *
 * Composite indexes on `(target, timestamp)` and `(sender, timestamp)` support
 * efficient history and pagination queries.
 *
 * @see MessageHistoryEntity for the DAO wrapper
 * @see xyz.malefic.irc.server.history.MessageHistoryService for query helpers
 */
object MessageHistoryTable : LongIdTable("message_history") {
    val timestamp = long("timestamp")
    val sender = varchar("sender", 50)
    val target = varchar("target", 50) // Channel name or recipient nickname
    val message = text("message")
    val messageType = varchar("message_type", 20).default("PRIVMSG") // PRIVMSG, NOTICE, etc.
    val isChannelMessage = bool("is_channel_message")

    /** Database ID of the message being replied to, or `null` for top-level messages. */
    val replyToId = long("reply_to_id").nullable()

    // Indexes for common queries
    init {
        index(false, target, timestamp)
        index(false, sender, timestamp)
    }
}

/**
 * Exposed DAO entity for a row in [MessageHistoryTable].
 *
 * @see MessageHistoryTable for the column definitions
 */
class MessageHistoryEntity(
    id: EntityID<Long>,
) : LongEntity(id) {
    companion object : LongEntityClass<MessageHistoryEntity>(MessageHistoryTable)

    var timestamp by MessageHistoryTable.timestamp
    var sender by MessageHistoryTable.sender
    var target by MessageHistoryTable.target
    var message by MessageHistoryTable.message
    var messageType by MessageHistoryTable.messageType
    var isChannelMessage by MessageHistoryTable.isChannelMessage
    var replyToId by MessageHistoryTable.replyToId
}
