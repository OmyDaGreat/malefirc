# Malefirc - IRC Client and Server System

A complete IRC (Internet Relay Chat) implementation in Kotlin, featuring a standards-compliant IRC server, terminal client, and web-based client. Compatible with standard IRC clients (irssi, HexChat, WeeChat) and servers.

## Architecture

This project is organized into multiple modules:

- **shared**: Shared IRC protocol implementation (RFC 1459/2812)
- **server**: Standalone IRC server (TCP socket server on port 6667)
- **client**: Terminal-based IRC client

## Features

### IRC Server
- ✅ RFC 1459/2812 compliant protocol implementation
- ✅ User connection management (NICK, USER registration)
- ✅ **User authentication (PASS command, SASL PLAIN)**
- ✅ **PostgreSQL database integration**
- ✅ **Password hashing with BCrypt**
- ✅ **Message history and persistence**
- ✅ **Full-text message search**
- ✅ **Message archival and cleanup**
- ✅ **Environment-based configuration (ports, credentials)**
- ✅ **User privacy settings (opt-out of logging)**
- ✅ **Authenticated message history API**
- ✅ Channel operations (JOIN, PART, TOPIC, NAMES, LIST)
- ✅ Message routing (PRIVMSG to channels and users)
- ✅ Server queries (WHO, LIST, NAMES)
- ✅ Automatic PING/PONG handling
- ✅ Multi-user channels with topic support
- ✅ Compatible with standard IRC clients

### IRC Client
- ✅ Terminal-based UI with command interface
- ✅ Channel and private messaging
- ✅ Automatic PING/PONG responses
- ✅ Channel management (/join, /part, /topic)
- ✅ User lists (/names, /who)
- ✅ Message display with proper formatting
- ✅ Connect to any RFC-compliant IRC server

## Quick Start

### Using Docker (Recommended)

The easiest way to run Malefirc is with Docker Compose:

```bash
# Start all services (PostgreSQL, IRC Server, Web Client)
docker-compose up -d

# View logs
docker-compose logs -f irc-server

# Stop services
docker-compose down
```

**Services:**
- IRC Server: `localhost:6667`
- Web Client: http://localhost:8080
- PostgreSQL: `localhost:5432`

**Security Note:** Change default credentials in `docker-compose.yml` for production:
```yaml
irc-server:
  environment:
    - IRC_OPER_NAME=your_admin
    - IRC_OPER_PASSWORD=your_secure_password
```

See [DOCKER.md](docs/DOCKER.md) for complete Docker documentation and [SECURITY.md](docs/SECURITY.md) for security best practices.

### Running Locally

#### Prerequisites
- JDK 17 or higher
- PostgreSQL (optional, for authentication)

### Running the IRC Server

```bash
# Build the server
./gradlew :irc-server:build

# Run the server
./gradlew :irc-server:run

# Or using the distribution
./irc-server/build/install/irc-server/bin/irc-server
```

The server starts on port **6667** by default (standard IRC port).

### Running the IRC Client

```bash
# Build the client
./gradlew :irc-client:build

# Run the client
./gradlew :irc-client:run

# Or using the distribution
./irc-client/build/install/irc-client/bin/irc-client
```

When prompted:
1. Enter server hostname (default: localhost)
2. Enter port (default: 6667)
3. Enter your nickname
4. Enter your username

### Client Commands

Once connected, you can use these commands:

- `/join #channel` - Join a channel
- `/part [reason]` - Leave current channel
- `/msg <target> <message>` - Send private message
- `/topic [new topic]` - Get or set channel topic
- `/list` - List all channels
- `/names` - List users in current channel
- `/quit [reason]` - Disconnect
- `/help` - Show help

Just type a message (without `/`) to send it to the current channel.

## Testing with Standard IRC Clients

The server is compatible with standard IRC clients. Here are some examples:

### Using irssi
```bash
irssi
/connect localhost 6667
/nick YourNick
/join #test
```

### Using HexChat
1. Add a new network with server `localhost/6667`
2. Connect and set your nickname
3. Join channels as normal

### Using WeeChat
```bash
weechat
/server add malefirc localhost/6667
/connect malefirc
/join #test
```

## Protocol Support

### Implemented Commands

**User Commands:**
- PASS - Password authentication
- NICK - Set nickname
- USER - User registration
- CAP - Capability negotiation
- AUTHENTICATE - SASL authentication
- JOIN - Join channels
- PART - Leave channels
- PRIVMSG - Send messages
- TOPIC - Get/set channel topic
- NAMES - List channel users
- LIST - List all channels
- WHO - Query user information
- QUIT - Disconnect
- PING/PONG - Connection keep-alive

**Numeric Replies:**
- 001-004: Welcome messages
- 322-323: LIST replies
- 332: Topic reply
- 353, 366: NAMES replies
- 401-502: Error codes
- 900-907: SASL authentication replies

See [AUTHENTICATION.md](docs/AUTHENTICATION.md) for authentication details.

## Development

### Building All Modules

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Project Structure

```
malefirc/
├── shared/       # Core IRC protocol (multiplatform)
│   └── src/commonMain/kotlin/xyz/malefic/irc/protocol/
│       ├── IRCMessage.kt      # Message parsing/serialization
│       ├── IRCCommand.kt      # Command constants
│       ├── IRCReply.kt        # Numeric reply codes
│       └── IRCMessageBuilder.kt # Helper functions
├── server/         # IRC server (JVM)
│   └── src/main/kotlin/xyz/malefic/irc/server/
│       ├── IRCServer.kt       # Main server implementation
│       ├── IRCModels.kt       # Data models
│       └── Main.kt            # Entry point
├── client/         # IRC client (JVM)
│   └── src/jvmMain/kotlin/xyz/malefic/irc/client/
│       ├── IRCClient.kt       # Client implementation
│       └── Main.kt            # Terminal UI
└── site/               # Web application (Kobweb)
```

## Roadmap

Future enhancements (see implementation plan for details):

### Phase 1: Authentication & Database ✅ COMPLETED
- [x] User authentication with password hashing (BCrypt)
- [x] SASL authentication (PLAIN mechanism)
- [x] PostgreSQL database integration
- [x] PASS command support

### Phase 2: Message Persistence ✅ COMPLETED
- [x] Message history storage in database
- [x] History retrieval API with pagination
- [x] Private message logging
- [x] Message search functionality
- [x] Automatic archival/cleanup system

See [MESSAGE_HISTORY.md](docs/MESSAGE_HISTORY.md) for complete documentation.

### Phase 3: User & Channel Modes ✅ COMPLETED
- [x] User modes (invisible, operator, away, etc.)
- [x] Channel operator (+o) and voice (+v) modes
- [x] Channel modes (moderated, secret, invite-only, etc.)
- [x] WHOIS command implementation

### Phase 4: Channel Management ✅ COMPLETED
- [x] Channel password protection (+k mode)
- [x] INVITE/KICK commands
- [x] BAN management via MODE command
- [x] Channel user limits (+l mode)
- [x] OPER command for server operators
- [x] AWAY command implementation

See [MODES.md](docs/MODES.md) for complete documentation.

### Phase 4.5: Security Enhancements ✅ COMPLETED
- [x] Environment-based OPER credentials (no hardcoded passwords)
- [x] User privacy settings (opt-out of message logging)
- [x] Authenticated message history API
- [x] Access control for private message history
- [x] Privacy-respecting search and history retrieval

See [SECURITY.md](docs/SECURITY.md) for security configuration and best practices.

### Phase 5: SSL/TLS Support
- [ ] SSL/TLS encryption
- [ ] Port 6697 for secure connections
- [ ] STARTTLS command support

### Phase 6: WebSocket Bridge
- [ ] WebSocket to IRC protocol bridge
- [ ] Real-time web client connection
- [ ] Message routing between protocols

### Phase 7: Advanced Features
- [ ] DCC file transfer support (optional)
- [ ] Connection throttling and rate limiting
- [ ] Advanced logging and metrics
- [ ] IRC services (NickServ, ChanServ)

## Contributing

This is an IRC implementation following RFC 1459 and RFC 2812 standards. Pull requests welcome!

## License

MIT License - See LICENSE file for details
