# Malefirc - IRC Client and Server System

A complete IRC (Internet Relay Chat) implementation in Kotlin, featuring a standards-compliant IRC server, terminal client, and web-based client. Compatible with standard IRC clients (irssi, HexChat, WeeChat) and servers.

## Architecture

This project is organized into multiple modules:

- **irc-protocol**: Shared IRC protocol implementation (RFC 1459/2812)
- **irc-server**: Standalone IRC server (TCP socket server on port 6667)
- **irc-client**: Terminal-based IRC client
- **core**: Common UI components for web client
- **auth**: Authentication and user management (web)
- **chat**: Chat UI (web)
- **site**: Web application (Kobweb-based)

## Features

### IRC Server
- ✅ RFC 1459/2812 compliant protocol implementation
- ✅ User connection management (NICK, USER registration)
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
- NICK - Set nickname
- USER - User registration
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
├── irc-protocol/       # Core IRC protocol (multiplatform)
│   └── src/commonMain/kotlin/xyz/malefic/irc/protocol/
│       ├── IRCMessage.kt      # Message parsing/serialization
│       ├── IRCCommand.kt      # Command constants
│       ├── IRCReply.kt        # Numeric reply codes
│       └── IRCMessageBuilder.kt # Helper functions
├── irc-server/         # IRC server (JVM)
│   └── src/jvmMain/kotlin/xyz/malefic/irc/server/
│       ├── IRCServer.kt       # Main server implementation
│       ├── IRCModels.kt       # Data models
│       └── Main.kt            # Entry point
├── irc-client/         # IRC client (JVM)
│   └── src/jvmMain/kotlin/xyz/malefic/irc/client/
│       ├── IRCClient.kt       # Client implementation
│       └── Main.kt            # Terminal UI
└── site/               # Web application (Kobweb)
```

## Roadmap

Future enhancements:
- [ ] User modes and channel modes (op, voice, etc.)
- [ ] Channel password protection
- [ ] Ban/kick functionality
- [ ] SSL/TLS support
- [ ] WebSocket bridge for web client
- [ ] Message persistence and history
- [ ] User authentication integration with PostgreSQL
- [ ] SASL authentication
- [ ] DCC file transfer support

## Contributing

This is an IRC implementation following RFC 1459 and RFC 2812 standards. Pull requests welcome!

## License

MIT License - See LICENSE file for details
