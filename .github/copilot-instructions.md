# Copilot Instructions for Malefirc

## Project Overview

Malefirc is an RFC-compliant IRC (Internet Relay Chat) implementation in Kotlin featuring:
- **Server**: Standalone IRC server (TCP socket server on port 6667)
- **Client**: Terminal-based IRC client + Kobweb web interface
- **Shared**: Multiplatform IRC protocol library (RFC 1459/2812)

Compatible with standard IRC clients (irssi, HexChat, WeeChat) and servers.

## Build & Run Commands

### Build Everything
```bash
./gradlew build
```

### Run IRC Server
```bash
./gradlew :server:run
# Or using convenience script:
./start-server.sh
```

### Run Terminal Client
```bash
./gradlew :client:run
# Or using convenience script:
./start-client.sh
```

### Run Web Client
```bash
# Start Kobweb development server
cd client
../gradlew kobwebStart -t

# Export production build
../gradlew kobwebExport
```

### Docker
```bash
# Start all services (PostgreSQL, IRC Server, Web Client)
docker-compose up -d

# View logs
docker-compose logs -f irc-server

# Stop services
docker-compose down
```

**Service Ports:**
- IRC Server: `localhost:6667`
- Web Client: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

### Tests
No automated test suite exists yet. Manual testing via `TESTING.md` instructions.

## Architecture

### Module Structure
- **`shared/`** - Multiplatform Kotlin library (JVM + JS)
  - IRC protocol implementation (message parsing, commands, replies)
  - Shared data models for authentication and chat
  - No platform-specific code
  
- **`server/`** - JVM application
  - TCP socket server using Ktor Network
  - User/channel/message state management
  - PostgreSQL integration (Exposed framework)
  - REST API endpoints under `xyz.malefic.irc.*/api/`
  
- **`client/`** - Kobweb multiplatform application
  - **JS target**: Compose HTML web UI
  - **JVM target**: Terminal client + Kobweb backend

### Key Architectural Patterns

**Protocol Layer** (`shared/src/commonMain/kotlin/xyz/malefic/irc/protocol/`):
- `IRCMessage` - Parses raw IRC protocol strings (prefix, command, params)
- `IRCCommand` - Constants for IRC commands (NICK, JOIN, PRIVMSG, etc.)
- `IRCReply` - Numeric reply codes (001-907)
- `IRCMessageBuilder` - Helper functions to construct protocol messages

**Server Architecture** (`server/src/main/kotlin/`):
- `IRCServer.kt` - Main server loop, handles client connections and message routing
- `IRCModels.kt` - In-memory state (IRCClient, IRCChannel, IRCUser)
- `auth/` - Authentication system (BCrypt hashing, SASL PLAIN support)
- `chat/` - Message history persistence and search
- Database access uses Exposed ORM with transaction blocks

**Authentication Flow**:
1. Client sends `PASS password` OR `CAP REQ :sasl` + `AUTHENTICATE PLAIN`
2. Server validates against PostgreSQL `account` table
3. BCrypt used for password hashing (never plaintext)
4. If auth fails, connection continues but user is unauthenticated

**Message Routing**:
- `PRIVMSG #channel` → Broadcast to all users in channel (except sender)
- `PRIVMSG nickname` → Direct message to specific user
- All messages automatically logged to `message_history` table

## Key Conventions

### Package Organization
Follow this structure when adding new features:
```
xyz.malefic.irc.<feature>/
  ├── api/           # REST endpoints (if applicable)
  ├── config/        # Configuration and initialization
  ├── model/         # Database tables and data classes
  └── util/          # Helper functions
```

### Database Access Pattern
Always use transaction blocks with Exposed:
```kotlin
transaction {
    // Database operations here
    Account.find { AccountTable.username eq user }.firstOrNull()
}
```

### IRC Message Building
Use helper functions from `IRCMessageBuilder` instead of manual string concatenation:
```kotlin
// Good
buildPrivMsg(target, message)

// Bad
":$nickname PRIVMSG $target :$message\r\n"
```

### Coroutines for Blocking I/O
Server uses `Dispatchers.IO` for socket operations and database access:
```kotlin
withContext(Dispatchers.IO) {
    // Blocking operation
}
```

### Environment Variables for Config

**Database Configuration:**
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- Defaults provided in `DatabaseConfig.kt`

**IRC Server Configuration:**
- `IRC_PORT` - Server port (default: 6667)
- `IRC_SERVER_NAME` - Server hostname (default: malefirc.local)
- `IRC_OPER_NAME` - Operator username (default: admin)
- `IRC_OPER_PASSWORD` - Operator password (default: adminpass)

**Security Note:** Change `IRC_OPER_NAME` and `IRC_OPER_PASSWORD` in production deployments.

## Important Implementation Details

### IRC Commands Implemented

**Connection & User:**
- PASS, NICK, USER, QUIT, OPER, AWAY
- CAP, AUTHENTICATE (SASL PLAIN)

**Channel Operations:**
- JOIN, PART, TOPIC, NAMES, LIST
- MODE (user and channel)
- INVITE, KICK

**Messaging:**
- PRIVMSG, NOTICE

**Queries:**
- WHO, WHOIS
- PING, PONG

### Message Format
All IRC messages follow the format: `[:<prefix>] <command> [<params>] [:<trailing>]\r\n`
- Messages MUST end with `\r\n` (not just `\n`)
- Trailing parameter (after `:`) can contain spaces
- Commands are case-insensitive, but typically uppercase

### Client State Management
Server maintains:
- `clients: Map<Socket, IRCClient>` - Active connections
- `channels: Map<String, IRCChannel>` - Active channels
- `users: Map<String, IRCUser>` - Registered users

**IRCChannel includes:**
- `operators: MutableSet<String>` - Channel operators (@)
- `voiced: MutableSet<String>` - Voiced users (+)
- `banList: MutableSet<String>` - Ban masks
- `inviteList: MutableSet<String>` - Invited users (for +i mode)
- `key: String?` - Channel password (for +k mode)
- `userLimit: Int?` - Max users (for +l mode)
- `modes: MutableSet<Char>` - Active modes (m, s, i, t, n, etc.)

**IRCUser includes:**
- `modes: MutableSet<Char>` - User modes (i, o, w)
- `awayMessage: String?` - Away status
- `channels: MutableSet<String>` - Channels user is in

### Channel Mode Enforcement

JOIN command checks (in order):
1. Ban list - rejects if user matches ban mask
2. Invite-only (+i) - requires invitation or operator action
3. Channel key (+k) - requires correct password
4. User limit (+l) - rejects if channel is full

PRIVMSG command checks:
1. No external messages (+n) - requires channel membership
2. Moderated (+m) - requires operator or voice status

TOPIC command checks:
1. Topic lock (+t) - requires operator status

First user to join a channel automatically becomes operator.

### Message History
All PRIVMSG commands are automatically logged to PostgreSQL:
- Use `MessageHistoryService` for queries (not direct SQL)
- Supports pagination via `before` timestamp parameter
- Full-text search with `searchMessages(query, target, limit)`

### SASL Authentication
Base64 encoding format for PLAIN: `\0username\0password`
```kotlin
val credentials = "\u0000$username\u0000$password"
val encoded = credentials.encodeToByteArray().encodeBase64()
```

## Documentation References

- **docs/security.adoc** - Security features, environment variables, privacy settings
- **docs/modes.adoc** - User/channel modes, operator commands, mode enforcement
- **docs/authentication.adoc** - Auth mechanisms, SASL flow, database schema
- **docs/message-history.adoc** - Message persistence API, search, cleanup
- **docs/docker.adoc** - Container setup, service configuration, troubleshooting
- **docs/testing.adoc** - Manual testing procedures with multiple clients
- **docs/index.adoc** - Documentation index and navigation hub
- **RFC 1459** - Original IRC protocol specification
- **RFC 2812** - Updated IRC protocol specification
