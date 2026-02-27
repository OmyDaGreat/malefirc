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
- ✅ **Implicit TLS on port 6697 (PEM, PKCS12, JKS; auto-renews Let's Encrypt certs)**
- ✅ **IRCv3 message tags (msgid, +reply for threaded conversations)**
- ✅ **@mention notifications via server NOTICE**
- ✅ **WebSocket bridge on port 6680 (raw IRC over WebSocket; `IRC_WS_PORT` configurable)**
- ✅ Channel operations (JOIN, PART, TOPIC, NAMES, LIST)
- ✅ Message routing (PRIVMSG to channels and users)
- ✅ Server queries (WHO, WHOIS, LIST, NAMES)
- ✅ Channel and user modes (+o, +v, +b, +k, +l, +m, +s, +i, +t, +n)
- ✅ Automatic PING/PONG handling
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
- IRC Server (TLS): `localhost:6697`
- WebSocket Bridge: `ws://localhost:6680/irc`
- Web Client: http://localhost:8080
- PostgreSQL: `localhost:5432`

**Security Note:** Change default credentials in `docker-compose.yml` for production:
```yaml
irc-server:
  environment:
    - IRC_OPER_NAME=your_admin
    - IRC_OPER_PASSWORD=your_secure_password
```

See [Docker](docs/docker.adoc) for complete Docker documentation and [Security](docs/security.adoc) for security best practices.

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
- CAP - Capability negotiation (sasl, message-tags, msgid)
- AUTHENTICATE - SASL authentication
- JOIN - Join channels
- PART - Leave channels
- PRIVMSG - Send messages (with @mention and +reply tag support)
- NOTICE - Send notices
- TOPIC - Get/set channel topic
- NAMES - List channel users
- LIST - List all channels
- WHO - Query user information
- WHOIS - Detailed user information
- INVITE - Invite user to channel
- KICK - Remove user from channel
- MODE - User and channel modes
- AWAY - Set away message
- OPER - Server operator elevation
- QUIT - Disconnect
- PING/PONG - Connection keep-alive

**Numeric Replies:**
- 001-004: Welcome messages
- 311-319, 330: WHOIS replies
- 321-323: LIST replies
- 332: Topic reply
- 352, 315: WHO replies
- 353, 366: NAMES replies
- 401-502: Error codes
- 670, 691: TLS replies
- 900-907: SASL authentication replies

See [Authentication](docs/authentication.adoc) for authentication details.

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

See [Message History](docs/message-history.adoc) for complete documentation.

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

See [Modes](docs/modes.adoc) for complete documentation.

### Phase 4.5: Security Enhancements ✅ COMPLETED
- [x] Environment-based OPER credentials (no hardcoded passwords)
- [x] User privacy settings (opt-out of message logging)
- [x] Authenticated message history API
- [x] Access control for private message history
- [x] Privacy-respecting search and history retrieval

See [Security](docs/security.adoc) for security configuration and best practices.

### Phase 5: TLS/SSL Support ✅ COMPLETED
- [x] Implicit TLS on dedicated port 6697 (IRC-over-TLS, RFC 7194)
- [x] PEM file support (direct Let's Encrypt / Certbot integration)
- [x] PKCS12 and JKS keystore support
- [x] Auto-reload of certificates on file change (no restart needed)
- [x] Self-signed certificate generation for development

See [TLS / SSL Support](docs/tls.adoc) for complete documentation.

### Phase 6: Modern Messaging ✅ COMPLETED
- [x] IRCv3 `message-tags` capability negotiation
- [x] Server-assigned `msgid` on every delivered message
- [x] Threaded replies via `+reply` client tag
- [x] Reply relationships stored and queryable in message history
- [x] `@mention` detection with server NOTICE to mentioned users
- [x] Tags stripped transparently for legacy clients

See [Client Messaging Guide](docs/usage-client.adoc) for complete documentation.

### Phase 7: Protocol Completeness
Core IRC features expected by virtually all clients that are currently missing.

#### 7a: Critical — most clients break without these
- [ ] **NOTICE routing** — clients and bots send NOTICEs; currently falls through to `ERR_UNKNOWNCOMMAND`
- [ ] **CTCP ACTION** (`/me`) — parse `\x01ACTION ...\x01` in PRIVMSG and display as `* nick text` in client
- [ ] **ISUPPORT (005 numeric)** — advertise server limits and supported features on connect (CHANTYPES, PREFIX, CHANMODES, NICKLEN, etc.)

#### 7b: Expected — most clients have UI for these
- [ ] **MOTD (Message of the Day)** — send 375/372/376 on registration; many clients wait for `RPL_ENDOFMOTD` before showing the server as ready
- [ ] **CTCP VERSION** — auto-reply to `\x01VERSION\x01` queries from other clients
- [ ] **CTCP PING** — pass through `\x01PING timestamp\x01` so clients can measure round-trip latency
- [ ] **WHOWAS** — return recent nickname history for departed users (311-style replies)
- [ ] **Client-side CTCP display** — render `/me`, VERSION, and PING results in the terminal client

#### 7c: Nice-to-have — improves experience on larger deployments
- [ ] **LIST filtering** — support `LIST >n` (min user count), `LIST #pattern*` (name glob), `LIST <n` (max user count)
- [ ] **ISON** — check presence of a list of nicknames in one command (used by buddy-list clients)
- [ ] **USERHOST** — return host info for up to 5 nicks at once
- [ ] **Rate limiting / flood protection** — token-bucket throttle per connection (~1 message / 500 ms with burst)

### Phase 8: WebSocket Bridge ✅ COMPLETED
- [x] WebSocket to IRC protocol bridge (raw IRC protocol over WebSocket text frames)
- [x] Real-time web client connection on port 6680 (`ws://<host>:6680/irc`)
- [x] Message routing between protocols via local TCP relay
- [x] `IRC_WS_ENABLED` / `IRC_WS_PORT` / `IRC_WS_HOST` environment variable configuration

See [WebSocket Bridge](docs/websocket.adoc) for complete WebSocket bridge documentation.

### Phase 9: Advanced Features
- [ ] IRC services (NickServ, ChanServ)
- [ ] Advanced logging and metrics
- [ ] DCC file transfer support (optional)

## Contributing

This is an IRC implementation following RFC 1459 and RFC 2812 standards. Pull requests welcome!

## License

MIT License - See LICENSE file for details
