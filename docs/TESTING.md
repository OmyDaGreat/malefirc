# Testing the IRC System

## Quick Test Guide

### 1. Start the IRC Server

In terminal 1:
```bash
./gradlew :irc-server:run
```

Wait for: `IRC Server started on port 6667`

### 2. Connect First Client

In terminal 2:
```bash
./gradlew :irc-client:run
```

When prompted:
- Server: `localhost`
- Port: `6667`
- Nickname: `alice`
- Username: `alice`

After connecting, join a channel:
```
/join #test
```

### 3. Connect Second Client

In terminal 3:
```bash
./gradlew :irc-client:run
```

When prompted:
- Server: `localhost`
- Port: `6667`
- Nickname: `bob`
- Username: `bob`

Join the same channel:
```
/join #test
```

### 4. Test Messaging

From alice's terminal:
```
Hello Bob!
```

Bob should see: `[#test] <alice> Hello Bob!`

From bob's terminal:
```
Hi Alice!
```

Alice should see: `[#test] <bob> Hi Alice!`

### 5. Test Commands

Try these commands in either client:

**List channels:**
```
/list
```

**See who's in the channel:**
```
/names
```

**Set a topic:**
```
/topic Welcome to #test!
```

**Send a private message:**
```
/msg alice Hey there!
```

### 6. Test with Standard IRC Client

Install irssi or use telnet:

**Using telnet (raw protocol):**
```bash
telnet localhost 6667
```

Then type:
```
NICK testuser
USER testuser 0 * :Test User
JOIN #test
PRIVMSG #test :Hello from telnet!
QUIT :Bye
```

**Using irssi:**
```bash
irssi
/connect localhost 6667
/nick charlie
/join #test
Hello everyone!
/quit
```

## Expected Behavior

### On JOIN:
- User sees: welcome messages, topic (if set), user list
- Other users see: `*** nickname has joined #channel`

### On PRIVMSG:
- Channel message: Everyone in channel sees it (except sender)
- Private message: Only recipient sees it

### On PART/QUIT:
- Everyone in channel sees: `*** nickname has left #channel (reason)`

### On TOPIC:
- Everyone in channel sees the new topic

## Common Issues

**"Connection refused":**
- Make sure the server is running
- Check if port 6667 is available

**"Nickname already in use":**
- Choose a different nickname
- Wait a moment if client just disconnected

**Messages not appearing:**
- Make sure you're in a channel (`/join #test`)
- Check you're sending to the right target

## Protocol Verification

To verify RFC compliance, test these scenarios:

1. **Multiple users in multiple channels**
   - User can join multiple channels
   - Messages go to correct channel
   - PART removes user from only that channel

2. **PING/PONG**
   - Server should respond to PING
   - Connection stays alive

3. **Error handling**
   - Invalid commands return ERR_UNKNOWNCOMMAND
   - Missing parameters return ERR_NEEDMOREPARAMS
   - Non-existent nicknames return ERR_NOSUCHNICK

4. **Case sensitivity**
   - Commands should be case-insensitive (NICK, nick, NiCk all work)
   - Nicknames are case-sensitive
   - Channel names are typically case-insensitive
