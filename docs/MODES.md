# IRC Modes & Channel Management

## Overview

Malefirc now supports comprehensive IRC modes and channel management features as defined in RFC 1459/2812. This includes user modes, channel modes, operator privileges, and advanced channel control commands.

## User Modes

User modes affect individual user behavior and visibility. Users can set their own modes (except +o).

### Supported User Modes

| Mode | Name | Description |
|------|------|-------------|
| `i` | Invisible | User is hidden from WHO/NAMES unless sharing a channel |
| `o` | Operator | User is a server operator (IRC op) |
| `w` | Wallops | User receives WALLOPS messages |

### User Mode Commands

**Query your own modes:**
```
MODE nickname
```

**Set invisible mode:**
```
MODE nickname +i
```

**Remove invisible mode:**
```
MODE nickname -i
```

**Example:**
```
MODE alice +i     # Alice becomes invisible
MODE alice -i     # Alice becomes visible again
```

## Channel Modes

Channel modes control channel behavior and access. Only channel operators can change most channel modes.

### Supported Channel Modes

| Mode | Name | Parameter | Description |
|------|------|-----------|-------------|
| `m` | Moderated | - | Only ops/voiced users can speak |
| `s` | Secret | - | Channel hidden from LIST unless you're in it |
| `i` | Invite-only | - | Must be invited to join |
| `t` | Topic lock | - | Only ops can change topic |
| `n` | No external | - | Only members can send messages |
| `l` | User limit | number | Maximum number of users |
| `k` | Key | password | Password required to join |
| `o` | Operator | nickname | Grant channel operator status |
| `v` | Voice | nickname | Grant voice (can speak in moderated) |
| `b` | Ban | mask | Ban user mask from channel |

### Channel Mode Commands

**Query channel modes:**
```
MODE #channel
```

**Set moderated mode:**
```
MODE #channel +m
```

**Set channel password:**
```
MODE #channel +k secretpass
```

**Set user limit:**
```
MODE #channel +l 50
```

**Grant operator status:**
```
MODE #channel +o alice
```

**Grant voice:**
```
MODE #channel +v bob
```

**Ban a user mask:**
```
MODE #channel +b *!*@*.example.com
```

**Combine modes:**
```
MODE #channel +mti         # Set moderated, topic-locked, invite-only
MODE #channel +ov alice bob # Op alice, voice bob
MODE #channel -m +k pass123 # Remove moderated, add key
```

**List bans:**
```
MODE #channel +b
```

## Channel Operator Commands

### KICK - Remove user from channel

**Syntax:**
```
KICK #channel nickname [reason]
```

**Requirements:**
- Must be channel operator
- Target must be in channel

**Examples:**
```
KICK #lobby spammer Spamming
KICK #dev alice Please rejoin with proper client
```

### INVITE - Invite user to channel

**Syntax:**
```
INVITE nickname #channel
```

**Requirements:**
- Must be in the channel
- Must be operator if channel is invite-only (+i)

**Examples:**
```
INVITE bob #private
INVITE alice #secret-channel
```

**Notes:**
- Invited users bypass invite-only restriction
- Invitation is consumed on first JOIN
- Users cannot invite themselves

### BAN Management

Bans prevent matching users from joining the channel.

**Ban a user:**
```
MODE #channel +b nick!user@host
```

**Common ban patterns:**
```
MODE #channel +b alice!*@*           # Ban nickname alice
MODE #channel +b *!*@evil.com        # Ban domain
MODE #channel +b *!baduser@*         # Ban username
MODE #channel +b *!*@192.168.1.*     # Ban IP range
```

**Remove a ban:**
```
MODE #channel -b alice!*@*
```

**List all bans:**
```
MODE #channel +b
```

## Server Operator Commands

### OPER - Become server operator

**Syntax:**
```
OPER name password
```

**Example:**
```
OPER admin adminpass
```

**Notes:**
- Credentials are configured server-side
- Default: `admin` / `adminpass` (testing environment only)
- Server operators can:
  - Grant channel operator status
  - Manage server-wide settings
  - Override channel restrictions

**Security Considerations:**
- The current implementation uses hardcoded credentials for development/testing
- Production deployments should use environment variables or secure config files
- Consider integrating with the existing authentication system (database-backed)
- Credentials should never be committed to version control

## User Query Commands

### WHOIS - Get user information

**Syntax:**
```
WHOIS nickname
```

**Example:**
```
WHOIS alice
```

**Response includes:**
- Username and hostname
- Real name
- Channels user is in (with @ for ops, + for voice)
- Server information
- Operator status
- Account name (if authenticated)
- Away message (if away)

### AWAY - Set/unset away status

**Set away:**
```
AWAY :Gone for lunch, back in 30 minutes
```

**Unset away:**
```
AWAY
```

**Notes:**
- Away message is shown in WHOIS
- Private messages to away users may trigger auto-reply (not implemented)

## Channel Mode Behavior

### Joining Channels

When a user attempts to JOIN a channel, these checks are performed in order:

1. **Ban check** - If user matches a ban mask, JOIN fails with ERR_BANNEDFROMCHAN
2. **Invite-only check** - If +i mode is set and user is not invited, JOIN fails with ERR_INVITEONLYCHAN
3. **Channel key check** - If +k mode is set and wrong/no key provided, JOIN fails with ERR_BADCHANNELKEY
4. **User limit check** - If +l mode is set and channel is full, JOIN fails with ERR_CHANNELISFULL

**Example with key:**
```
JOIN #secret wrongpass      # Fails
JOIN #secret rightpass      # Succeeds if correct key
```

**Example with multiple channels:**
```
JOIN #one,#two,#three pass1,pass2,pass3
```

### Sending Messages

When a user attempts to PRIVMSG a channel, these checks are performed:

1. **No external check** - If +n mode is set and user is not in channel, PRIVMSG fails
2. **Moderated check** - If +m mode is set and user is not op/voiced, PRIVMSG fails

### Setting Topic

When a user attempts to change the channel topic:

1. **Topic lock check** - If +t mode is set and user is not operator, TOPIC change fails

### Names Display

The NAMES list shows prefixes for users with special status:
- `@nickname` - Channel operator
- `+nickname` - Voiced user
- `nickname` - Regular user

## Mode Enforcement Examples

### Scenario 1: Moderated Channel

```
# Operator creates moderated channel
JOIN #moderated
MODE #moderated +m

# User bob joins
# Bob tries to speak but cannot (not voiced)
# Operator gives bob voice
MODE #moderated +v bob
# Now bob can speak
```

### Scenario 2: Private Channel

```
# Create private channel with password
JOIN #private
MODE #private +istk secretpass
# +i (invite-only), +s (secret), +t (topic lock), +k (key)

# Channel is hidden from /LIST
# Users must be invited or know the key
# Only ops can change topic
```

### Scenario 3: Managed Community Channel

```
# Create well-managed channel
JOIN #community
MODE #community +nt
# +n (no external messages), +t (topic lock)

# Set user limit
MODE #community +l 100

# Grant ops to moderators
MODE #community +ooo alice bob charlie

# Moderators can kick troublemakers
KICK #community spammer Repeated spam warnings

# Ban if necessary
MODE #community +b *!*@spammer.evil.com
```

## Technical Implementation

### First User Privilege

When a channel is created (first user joins), that user automatically becomes a channel operator. This allows initial channel setup.

### Operator Privileges

Channel operators can:
- Change all channel modes
- Grant/revoke operator and voice status
- Kick users
- Set topic (regardless of +t mode)
- Invite users to invite-only channels

### Mode Persistence

**Current Implementation:**
- Modes persist as long as the channel exists
- Channels are destroyed when the last user leaves
- Modes reset on channel recreation

**Future Enhancement:**
- Persistent channels with mode/ops saved to database
- Channel registration system
- Founder/owner roles

### Ban Matching

Ban masks use wildcard matching:
- `*` matches any number of characters
- `?` matches exactly one character
- Format: `nickname!username@hostname`

**Examples:**
- `*!*@*.example.com` - Bans all users from example.com domain
- `badnick!*@*` - Bans specific nickname regardless of host
- `*!baduser@*` - Bans specific username regardless of nick/host

## Error Codes

| Code | Name | Meaning |
|------|------|---------|
| 441 | ERR_USERNOTINCHANNEL | Target user not in channel (KICK) |
| 442 | ERR_NOTONCHANNEL | You're not on that channel |
| 443 | ERR_USERONCHANNEL | User already on channel (INVITE) |
| 471 | ERR_CHANNELISFULL | Channel is full (+l) |
| 472 | ERR_UNKNOWNMODE | Unknown mode flag |
| 473 | ERR_INVITEONLYCHAN | Invite only (+i) |
| 474 | ERR_BANNEDFROMCHAN | You're banned (+b) |
| 475 | ERR_BADCHANNELKEY | Wrong channel key (+k) |
| 481 | ERR_NOPRIVILEGES | No server operator privileges |
| 482 | ERR_CHANOPRIVSNEEDED | You're not channel operator |
| 501 | ERR_UMODEUNKNOWNFLAG | Unknown user mode flag |
| 502 | ERR_USERSDONTMATCH | Can't change mode for other users |

## Compatibility

### Standard IRC Clients

The mode implementation is compatible with standard IRC clients:
- **irssi**: Full support for all modes and commands
- **HexChat**: Full support with GUI mode management
- **WeeChat**: Full support with /mode, /op, /voice shortcuts
- **mIRC**: Compatible with standard mode syntax

### Mode Command Shortcuts

Many clients provide shortcuts:
```
/op alice           → MODE #channel +o alice
/deop alice         → MODE #channel -o alice
/voice bob          → MODE #channel +v bob
/devoice bob        → MODE #channel -v bob
/kick alice         → KICK #channel alice
/invite bob         → INVITE bob #channel
```

## Testing

See [TESTING.md](TESTING.md) for test procedures. Key test scenarios:

1. **User Modes**: Set/unset +i, +o, +w
2. **Channel Creation**: First user becomes op
3. **Mode Changes**: Ops can change modes, non-ops cannot
4. **Operator Status**: Grant/revoke +o and +v
5. **Bans**: Add bans, test ban matching, list bans
6. **Moderated Channel**: +m mode enforcement
7. **Invite-Only**: +i mode with INVITE command
8. **Topic Lock**: +t mode enforcement
9. **Channel Keys**: +k mode with correct/wrong keys
10. **User Limits**: +l mode enforcement
11. **KICK Command**: Remove users from channel
12. **WHOIS Command**: View user information
13. **AWAY Command**: Set/unset away status
14. **OPER Command**: Become server operator

## Future Enhancements

- [ ] Persistent channels with database storage
- [ ] Channel founder/owner roles
- [ ] Timed bans with automatic expiry
- [ ] Exception lists (ban exceptions)
- [ ] Quiet mode (mute instead of ban)
- [ ] Channel registration services (ChanServ)
- [ ] Mode locks (prevent certain mode changes)
- [ ] Channel access lists (auto-op/voice)
- [ ] SSL/TLS user mode (+Z)
- [ ] Bot mode (+B)

## License

MIT License - See LICENSE file for details
