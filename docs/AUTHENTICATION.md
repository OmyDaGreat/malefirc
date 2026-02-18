# Authentication Implementation Guide

## Overview

Malefirc now supports user authentication through multiple mechanisms:
1. **PASS command** - Traditional IRC password authentication
2. **SASL PLAIN** - Modern SASL authentication protocol

## Database Schema

User accounts are stored in PostgreSQL with the following fields:
- `username` - Unique username (max 50 chars)
- `password` - BCrypt hashed password (60 chars)
- `email` - Optional email address
- `createdAt` - Account creation timestamp
- `lastLogin` - Last successful login timestamp
- `isVerified` - Email verification status

## Authentication Methods

### 1. PASS Command (Traditional)

The PASS command must be sent before NICK and USER commands:

```irc
PASS mypassword
NICK myusername
USER myusername 0 * :Real Name
```

If an account exists with matching username/password, the user will be automatically authenticated upon registration.

### 2. SASL PLAIN

Modern IRC clients use SASL for authentication. The flow is:

```irc
CAP LS 302
CAP REQ :sasl
AUTHENTICATE PLAIN
AUTHENTICATE <base64-encoded-credentials>
CAP END
NICK myusername
USER myusername 0 * :Real Name
```

The base64 credentials format is: `\0username\0password`

**Example in Python:**
```python
import base64
credentials = f"\0{username}\0{password}"
encoded = base64.b64encode(credentials.encode()).decode()
```

### Supported IRC Numeric Replies

- `900 RPL_LOGGEDIN` - Successful authentication notification
- `901 RPL_LOGGEDOUT` - Logged out notification  
- `903 RPL_SASLSUCCESS` - SASL authentication successful
- `904 RPL_SASLFAIL` - SASL authentication failed
- `905 RPL_SASLTOOLONG` - SASL message too long
- `906 RPL_SASLABORTED` - SASL authentication aborted
- `907 RPL_SASLALREADY` - Already authenticated

## Account Registration

Accounts must be registered out-of-band (not via IRC commands yet). You can use the web API or directly insert into the database:

```kotlin
AuthenticationService.registerAccount(
    username = "myuser",
    password = "mypassword",
    email = "user@example.com" // optional
)
```

## Database Configuration

The server connects to PostgreSQL using environment variables:

- `DB_HOST` - Database host (default: localhost)
- `DB_PORT` - Database port (default: 5432)
- `DB_NAME` - Database name (default: malefirc)
- `DB_USER` - Database user (default: malefirc)
- `DB_PASSWORD` - Database password (default: malefirc)

## Security Features

1. **BCrypt Password Hashing** - Passwords are hashed with BCrypt (work factor 10)
2. **No Plaintext Storage** - Passwords are never stored in plaintext
3. **Optional Authentication** - Server works with or without authentication
4. **Database Connection Resilience** - Server continues without auth if DB unavailable

## Client Examples

### Using WeeChat with SASL

```
/server add malefirc localhost/6667
/set irc.server.malefirc.sasl_mechanism plain
/set irc.server.malefirc.sasl_username myusername
/set irc.server.malefirc.sasl_password mypassword
/connect malefirc
```

### Using irssi with PASS

```
/connect localhost 6667 mypassword myusername
```

### Using HexChat

1. Edit network settings
2. Select "Use SSL for all servers on this network" (if using SSL)
3. Set login method to "SASL PLAIN"
4. Enter username and password
5. Or use Server Password field for PASS command

## Testing Authentication

1. Start Docker services:
```bash
docker-compose up -d
```

2. Create a test account (via PostgreSQL):
```sql
-- Connect to database
psql -h localhost -p 5432 -U malefirc -d malefirc

-- Check if account exists
SELECT * FROM account WHERE username = 'testuser';

-- No IRC command for registration yet, must be done externally
```

3. Connect with any IRC client using SASL or PASS

## Future Enhancements

- [ ] IRC command for account registration (`/msg NickServ REGISTER`)
- [ ] Email verification system
- [ ] Password reset functionality
- [ ] Account deletion command
- [ ] Account linking (multiple nicks to one account)
- [ ] 2FA/TOTP support
- [ ] Rate limiting for failed auth attempts
- [ ] IP-based throttling
