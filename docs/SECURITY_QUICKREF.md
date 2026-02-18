# Quick Reference: Security Features

## Environment Variables

### IRC Server Configuration
Set these in your environment before starting the server:

```bash
# Server settings
export IRC_PORT=6667
export IRC_SERVER_NAME=malefirc.local

# Operator credentials (CHANGE IN PRODUCTION!)
export IRC_OPER_NAME=admin
export IRC_OPER_PASSWORD=adminpass

# Start server
./gradlew :server:run
```

### Docker Configuration
Edit `docker-compose.yml`:

```yaml
irc-server:
  environment:
    # CHANGE THESE VALUES IN PRODUCTION!
    - IRC_OPER_NAME=your_secure_username
    - IRC_OPER_PASSWORD=your_very_secure_password
    - IRC_PORT=6667
    - IRC_SERVER_NAME=malefirc.example.com
```

Then restart:
```bash
docker-compose up -d --force-recreate irc-server
```

## User Privacy Settings

### Database Schema
```sql
-- Account table includes privacy settings
CREATE TABLE account (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(60) NOT NULL,
    email VARCHAR(255),
    created_at BIGINT,
    last_login BIGINT,
    is_verified BOOLEAN DEFAULT false,
    allow_message_logging BOOLEAN DEFAULT true,   -- NEW
    allow_history_access BOOLEAN DEFAULT true     -- NEW
);
```

### Setting Privacy Options

**Option 1: Direct SQL**
```sql
-- Disable message logging for a user
UPDATE account 
SET allow_message_logging = false 
WHERE username = 'privacy_user';

-- Disable history access
UPDATE account 
SET allow_history_access = false 
WHERE username = 'privacy_user';
```

**Option 2: Via Application Code**
```kotlin
transaction {
    val account = AccountEntity.find { 
        AccountTable.username eq "privacy_user" 
    }.firstOrNull()
    
    account?.apply {
        allowMessageLogging = false  // Messages won't be saved
        allowHistoryAccess = false   // Messages won't appear in API
    }
}
```

## Message History API Changes

### Authentication Required

All API methods now require the `requestingUser` parameter:

```kotlin
// ❌ OLD (no longer works)
MessageHistoryAPI.getChannelHistory("#general", limit = 100)

// ✅ NEW (authentication required)
MessageHistoryAPI.getChannelHistory(
    requestingUser = "authenticated_username",
    channelName = "#general",
    limit = 100
)
```

### Access Control Rules

1. **Channel History**: Requires authentication, filters by privacy settings
2. **Private Messages**: Only conversation participants can access
3. **Search**: Requires authentication, respects privacy settings
4. **By Sender**: Users can only access their own messages
5. **Message Count**: Requires authentication

### Example API Usage

```kotlin
// Channel history (authenticated users only)
val channelHistory = MessageHistoryAPI.getChannelHistory(
    requestingUser = currentUser,
    channelName = "#general",
    limit = 50,
    before = System.currentTimeMillis()
)

// Private messages (participants only)
val privateHistory = MessageHistoryAPI.getPrivateHistory(
    requestingUser = currentUser,  // Must be user1 or user2
    user1 = currentUser,
    user2 = "otherUser",
    limit = 100
)

// Search (authenticated, privacy-filtered)
val searchResults = MessageHistoryAPI.searchMessages(
    requestingUser = currentUser,
    query = "important",
    target = "#general",  // Optional
    limit = 50
)

// Own messages only
val myMessages = MessageHistoryAPI.getMessagesBySender(
    requestingUser = currentUser,
    sender = currentUser,  // Must match requestingUser
    limit = 100
)

// Message count
val count = MessageHistoryAPI.getMessageCount(
    requestingUser = currentUser,
    target = "#general"
)
```

## IRC OPER Command

### Using OPER Command

```
/OPER <username> <password>
```

**Example:**
```
/OPER admin adminpass
```

**Response (success):**
```
:malefirc.local 381 yournick :You are now an IRC operator
```

**Response (failure):**
```
:malefirc.local 464 yournick :Password incorrect
```

### Changing OPER Credentials

**For Docker deployments:**
1. Edit `docker-compose.yml`
2. Change `IRC_OPER_NAME` and `IRC_OPER_PASSWORD`
3. Restart: `docker-compose up -d --force-recreate irc-server`

**For standalone deployments:**
```bash
export IRC_OPER_NAME=myadmin
export IRC_OPER_PASSWORD=MySecureP@ssw0rd123!
./gradlew :server:run
```

## Privacy Behavior

### Message Logging

When `allow_message_logging = false`:
- User's messages are **not saved** to database
- No history entries created
- Existing messages unaffected
- Real-time message delivery works normally

### History Access

When `allow_history_access = false`:
- User's messages **filtered out** from API responses
- Messages still in database (if logging was enabled)
- User's own messages still hidden from API
- Does not affect real-time messaging

### Both Disabled

When both are `false`:
- New messages not saved ✅
- Existing messages hidden ✅
- Maximum privacy ✅

## Defaults

All settings have safe defaults:

| Setting | Default | Meaning |
|---------|---------|---------|
| `IRC_OPER_NAME` | `admin` | Operator username |
| `IRC_OPER_PASSWORD` | `adminpass` | Operator password |
| `IRC_PORT` | `6667` | Server port |
| `IRC_SERVER_NAME` | `malefirc.local` | Server hostname |
| `allow_message_logging` | `true` | Messages are logged |
| `allow_history_access` | `true` | History accessible |

**⚠️ IMPORTANT:** Change `IRC_OPER_NAME` and `IRC_OPER_PASSWORD` in production!

## Checking Configuration

### Verify Environment Variables

```bash
docker-compose exec irc-server env | grep IRC_
```

Expected output:
```
IRC_PORT=6667
IRC_SERVER_NAME=malefirc.local
IRC_OPER_NAME=admin
IRC_OPER_PASSWORD=adminpass
```

### Verify Privacy Settings

```sql
SELECT username, allow_message_logging, allow_history_access 
FROM account;
```

## Troubleshooting

### OPER Command Not Working

1. Check environment variables are set
2. Verify credentials match environment
3. Check server logs for errors
4. Try default credentials (admin/adminpass)

### Messages Still Being Logged

1. Verify `allow_message_logging` is `false` in database
2. Check if user is authenticated (unauthenticated users may behave differently)
3. Restart server to pick up schema changes

### API Authentication Errors

1. Ensure `requestingUser` parameter is provided
2. Check username matches authenticated user
3. Verify API method signature matches new format
4. Review error message in response JSON

### Docker Environment Variables Not Applied

1. Edit `docker-compose.yml`
2. Run: `docker-compose down`
3. Run: `docker-compose up -d`
4. Verify with: `docker-compose exec irc-server env | grep IRC_`

## Security Checklist

Production deployment checklist:

- [ ] Changed `IRC_OPER_NAME` from default
- [ ] Changed `IRC_OPER_PASSWORD` to strong password (16+ chars)
- [ ] Changed database password (`DB_PASSWORD`)
- [ ] Documented operator credentials securely
- [ ] Configured privacy settings for users
- [ ] Updated API clients to use authentication
- [ ] Tested OPER command with new credentials
- [ ] Reviewed [SECURITY.md](./SECURITY.md)
- [ ] Configured backup strategy
- [ ] Set up monitoring/logging

## Additional Resources

- **Full Security Guide:** [docs/SECURITY.md](./SECURITY.md)
- **Implementation Details:** [docs/SECURITY_IMPLEMENTATION.md](./SECURITY_IMPLEMENTATION.md)
- **Message History API:** [docs/MESSAGE_HISTORY.md](./MESSAGE_HISTORY.md)
- **Docker Setup:** [docs/DOCKER.md](./DOCKER.md)
