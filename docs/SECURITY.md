# Security Features

## Overview

This document describes the security features implemented in the MalefIRC server.

## Environment Variable Configuration

### IRC Server Operator Credentials

The IRC server operator (OPER) credentials are now configured via environment variables instead of being hardcoded.

**Environment Variables:**
- `IRC_OPER_NAME`: Operator username (default: "admin")
- `IRC_OPER_PASSWORD`: Operator password (default: "adminpass")

**Docker Configuration:**

Update `docker-compose.yml` to set these variables:

```yaml
irc-server:
  environment:
    - IRC_OPER_NAME=your_admin_username
    - IRC_OPER_PASSWORD=your_secure_password
```

**Standalone Configuration:**

Set environment variables before starting the server:

```bash
export IRC_OPER_NAME=myadmin
export IRC_OPER_PASSWORD=mySecureP@ssw0rd
./gradlew :server:run
```

### Additional Server Configuration

Other configurable environment variables:

- `IRC_PORT`: Server port (default: 6667)
- `IRC_SERVER_NAME`: Server hostname (default: "malefirc.local")

## User Privacy Settings

### Message Logging Control

Users can control whether their messages are logged to the database history.

**Database Schema:**

The `account` table includes privacy settings:

```sql
allow_message_logging BOOLEAN DEFAULT true
allow_history_access BOOLEAN DEFAULT true
```

**Privacy Settings:**

1. **`allow_message_logging`**: Controls whether messages sent by this user are logged
   - Default: `true` (messages are logged)
   - When `false`: User's messages are not saved to message history

2. **`allow_history_access`**: Controls whether this user's message history can be retrieved
   - Default: `true` (history can be accessed)
   - When `false`: User's messages are filtered out from history API responses

### Message History API Authentication

All message history API endpoints now require authentication:

**Channel History:**
```kotlin
MessageHistoryAPI.getChannelHistory(
    requestingUser = "authenticated_user",
    channelName = "#general",
    limit = 100
)
```
- Requires authenticated user
- Filters out messages from users who disabled history access

**Private Message History:**
```kotlin
MessageHistoryAPI.getPrivateHistory(
    requestingUser = "user1",
    user1 = "user1",
    user2 = "user2",
    limit = 100
)
```
- Requires authentication
- User can only access their own private messages
- Both participants must allow history access

**Search Messages:**
```kotlin
MessageHistoryAPI.searchMessages(
    requestingUser = "authenticated_user",
    query = "search term",
    limit = 50
)
```
- Requires authentication
- Results filtered by privacy settings

**Get Messages by Sender:**
```kotlin
MessageHistoryAPI.getMessagesBySender(
    requestingUser = "username",
    sender = "username",
    limit = 100
)
```
- Requires authentication
- Users can only access their own message history
- Respects privacy settings

## Database Security

### PostgreSQL Configuration

Database credentials are configured via environment variables:

- `DB_HOST`: Database host (default: "localhost")
- `DB_PORT`: Database port (default: 5432)
- `DB_NAME`: Database name (default: "malefirc")
- `DB_USER`: Database username (default: "malefirc")
- `DB_PASSWORD`: Database password (default: "malefirc")

**Production Recommendations:**
1. Use strong, unique passwords
2. Restrict database access to application servers only
3. Use SSL/TLS for database connections
4. Regularly backup the database
5. Monitor for unusual access patterns

### Password Hashing

User account passwords are hashed using BCrypt before storage:
- Strong, adaptive hashing algorithm
- Per-password salt
- Resistant to rainbow table attacks

## Best Practices

### For Operators

1. **Change Default Credentials**: Always change `IRC_OPER_NAME` and `IRC_OPER_PASSWORD` in production
2. **Use Strong Passwords**: Use passwords with:
   - At least 16 characters
   - Mix of uppercase, lowercase, numbers, and symbols
   - No dictionary words or common patterns
3. **Limit Operator Access**: Only grant operator status to trusted administrators
4. **Monitor Operator Actions**: Keep logs of operator commands (KICK, BAN, etc.)

### For Users

1. **Review Privacy Settings**: Check your `allow_message_logging` and `allow_history_access` settings
2. **Use Strong Passwords**: Same requirements as operators
3. **Enable Two-Factor Authentication**: (Future feature)
4. **Be Cautious with Private Information**: Even with logging disabled, messages pass through the server

### For Administrators

1. **Regular Security Audits**: Review logs, access patterns, and configurations
2. **Keep Software Updated**: Apply security patches promptly
3. **Use HTTPS/TLS**: Encrypt all web and IRC connections (Phase 5 feature)
4. **Implement Rate Limiting**: Prevent brute force attacks
5. **Backup Regularly**: Maintain secure, encrypted backups
6. **Monitor Resource Usage**: Watch for DoS attempts
7. **Isolate Services**: Run IRC server in containerized/sandboxed environment

## Future Security Enhancements

Planned security features for future releases:

1. **SSL/TLS Support** (Phase 5): Encrypted IRC connections
2. **SASL EXTERNAL**: Certificate-based authentication
3. **Rate Limiting**: Per-user and per-IP connection limits
4. **IP Banning**: Automated ban system for abusive clients
5. **Two-Factor Authentication**: TOTP/U2F support for accounts
6. **Audit Logging**: Comprehensive logging of all operator actions
7. **Password Policies**: Configurable password strength requirements
8. **Session Management**: Token-based authentication with expiry
9. **GDPR Compliance**: Data export and deletion tools

## Reporting Security Issues

If you discover a security vulnerability:

1. **Do NOT** open a public issue
2. Contact the maintainers privately
3. Provide detailed information about the vulnerability
4. Allow time for a fix before public disclosure

## Compliance

### GDPR Considerations

The privacy settings help with GDPR compliance:
- Users can opt out of message logging (Right to Object)
- Users can disable history access (Right to Erasure - partial)
- Message history can be deleted (Right to Erasure - full)

**Note**: Full GDPR compliance requires additional features like data export and comprehensive deletion tools.

### Data Retention

Configure automatic cleanup of old messages:

```kotlin
// Delete messages older than 90 days
MessageHistoryService.cleanupMessagesOlderThan(90)
```

Consider implementing automated cleanup policies based on:
- Legal requirements
- Storage constraints
- User privacy preferences

## References

- [RFC 1459](https://tools.ietf.org/html/rfc1459) - Internet Relay Chat Protocol
- [OWASP Top 10](https://owasp.org/www-project-top-ten/) - Web Application Security Risks
- [NIST Password Guidelines](https://pages.nist.gov/800-63-3/) - Digital Identity Guidelines
