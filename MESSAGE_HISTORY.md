# Message History & Persistence

## Overview

Malefirc now includes a comprehensive message history system that stores all IRC messages in PostgreSQL. This enables message retrieval, search, and archival capabilities.

## Features

- ✅ Automatic logging of all channel and private messages
- ✅ Message history retrieval with pagination
- ✅ Full-text message search
- ✅ Private message history between users
- ✅ Message archival and cleanup system
- ✅ RESTful API for history access

## Database Schema

### message_history Table

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Unique message ID (auto-increment) |
| timestamp | BIGINT | Unix timestamp in milliseconds |
| sender | VARCHAR(50) | Nickname of message sender |
| target | VARCHAR(50) | Channel name or recipient nickname |
| message | TEXT | Message content |
| message_type | VARCHAR(20) | Message type (PRIVMSG, NOTICE, etc.) |
| is_channel_message | BOOLEAN | True if channel message, false if private |

**Indexes:**
- `(target, timestamp)` - Fast lookups by channel/user
- `(sender, timestamp)` - Fast lookups by sender

## API Functions

### MessageHistoryService

All message history operations are handled through the `MessageHistoryService` object:

#### Get Channel History

```kotlin
val history = MessageHistoryService.getChannelHistory(
    channelName = "#general",
    limit = 100,
    before = optionalTimestamp
)
```

Returns up to `limit` messages from the channel, optionally before a specific timestamp.

#### Get Private Message History

```kotlin
val history = MessageHistoryService.getPrivateHistory(
    user1 = "alice",
    user2 = "bob",
    limit = 100,
    before = optionalTimestamp
)
```

Returns private messages between two users, regardless of direction.

#### Search Messages

```kotlin
val results = MessageHistoryService.searchMessages(
    searchQuery = "keyword",
    target = "#general",  // optional, null for all channels
    limit = 50
)
```

Full-text search across message content.

#### Get Messages by Sender

```kotlin
val messages = MessageHistoryService.getMessagesBySender(
    sender = "alice",
    limit = 100
)
```

Retrieve all messages sent by a specific user.

#### Cleanup Old Messages

```kotlin
// Delete messages older than 30 days
val deletedCount = MessageHistoryService.cleanupMessagesOlderThan(days = 30)

// Or use specific timestamp
val cutoff = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
val deletedCount = MessageHistoryService.cleanupOldMessages(cutoff)
```

## REST API (MessageHistoryAPI)

The `MessageHistoryAPI` object provides JSON endpoints for web clients:

### Get Channel History

```kotlin
val json = MessageHistoryAPI.getChannelHistory(
    channelName = "#general",
    limit = 100,
    before = null
)
```

**Response:**
```json
{
  "success": true,
  "messages": [
    {
      "id": 1,
      "timestamp": 1707865200000,
      "sender": "alice",
      "target": "#general",
      "message": "Hello everyone!",
      "messageType": "PRIVMSG",
      "isChannelMessage": true
    }
  ]
}
```

### Search Messages

```kotlin
val json = MessageHistoryAPI.searchMessages(
    query = "hello",
    target = "#general",
    limit = 50
)
```

### Get Message Count

```kotlin
val json = MessageHistoryAPI.getMessageCount("#general")
```

**Response:**
```json
{
  "success": true,
  "count": 1234
}
```

## Automatic Message Logging

All messages sent via PRIVMSG are automatically logged:

- **Channel messages** - Logged when broadcast to channel
- **Private messages** - Logged when sent to recipient
- **Message type** - Defaults to "PRIVMSG", can be extended for NOTICE, etc.

## Performance Considerations

### Indexes

The database includes indexes on:
- `(target, timestamp)` - Optimizes channel history queries
- `(sender, timestamp)` - Optimizes sender-based queries

### Query Optimization

- History queries use `LIMIT` to prevent loading too many records
- Pagination supported via `before` timestamp parameter
- Search queries use SQL `LIKE` (consider full-text search for production)

### Memory Usage

- Queries use Kotlin collections for filtering
- Consider adding database-level filtering for large deployments
- Use pagination to limit memory consumption

## Message Retention Policy

Configure automatic cleanup using a scheduled task:

```kotlin
// Example: Daily cleanup of messages older than 90 days
val deletedCount = MessageHistoryService.cleanupMessagesOlderThan(days = 90)
println("Deleted $deletedCount old messages")
```

## Privacy Considerations

### Data Retention

- Private messages are stored indefinitely by default
- Implement retention policies based on your requirements
- Consider GDPR/privacy regulations for user data

### Access Control

- The API currently has no access control
- **TODO:** Add authentication checks before returning history
- **TODO:** Implement user privacy settings (opt-out of logging)

### Data Protection

- Messages are stored in plaintext in PostgreSQL
- Consider encryption at rest for sensitive deployments
- Secure database connection credentials

## Future Enhancements

- [ ] Database-level full-text search (PostgreSQL FTS)
- [ ] Message edit/delete tracking
- [ ] User opt-out of message logging
- [ ] Encrypted message storage
- [ ] Advanced search filters (date range, sender, regex)
- [ ] Message statistics and analytics
- [ ] Export functionality (JSON, CSV, mbox)
- [ ] Automatic archival to object storage (S3)
- [ ] Read receipts and message status tracking

## Example Usage

### Retrieve Last 50 Messages from a Channel

```kotlin
val messages = MessageHistoryService.getChannelHistory(
    channelName = "#general",
    limit = 50
)

messages.forEach { msg ->
    println("${msg.sender}: ${msg.message}")
}
```

### Search for Mentions

```kotlin
val mentions = MessageHistoryService.searchMessages(
    searchQuery = "@alice",
    target = null,  // Search all channels
    limit = 100
)
```

### Get Conversation Between Two Users

```kotlin
val conversation = MessageHistoryService.getPrivateHistory(
    user1 = "alice",
    user2 = "bob",
    limit = 100
)
```

## Database Migrations

The message_history table is automatically created on server startup via `DatabaseConfig.connect()`. No manual migration required.

To reset history:
```sql
TRUNCATE TABLE message_history;
```

To backup history:
```bash
pg_dump -h localhost -U malefirc -d malefirc -t message_history > history_backup.sql
```

## Troubleshooting

### Messages not being logged

1. Check database connection in server logs
2. Verify MessageHistoryTable is created: `\dt` in psql
3. Check for transaction errors in server output

### Slow queries

1. Verify indexes exist: `\d message_history` in psql
2. Add database-level filtering instead of Kotlin filtering
3. Reduce `limit` parameter in queries
4. Consider partitioning table by date for large datasets

### Disk space issues

1. Implement regular cleanup of old messages
2. Archive old messages to separate storage
3. Monitor database size: `SELECT pg_size_pretty(pg_database_size('malefirc'));`

## License

MIT License - See LICENSE file for details
