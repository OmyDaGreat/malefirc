package xyz.malefic.irc.server

/**
 * Represents an IRC user connected to the server.
 *
 * This data class tracks all state for a connected user, including their identity,
 * registration status, authentication, channel memberships, and user modes.
 *
 * ## Lifecycle
 * 1. User connects - [hostname] is set from socket
 * 2. User sends NICK - [nickname] is set
 * 3. User sends USER - [username] and [realname] are set
 * 4. Registration completes - [registered] becomes true
 * 5. User may authenticate - [authenticated] and [accountName] are set
 *
 * @property nickname User's current nickname (set via NICK command)
 * @property username User's username/ident (set via USER command)
 * @property realname User's real name or description (set via USER command)
 * @property hostname User's hostname or IP address (from socket connection)
 * @property channels Set of channel names the user is currently in
 * @property registered Whether the user has completed NICK + USER registration
 * @property password Password provided via PASS command (before registration)
 * @property authenticated Whether the user successfully authenticated
 * @property accountName Account name if authenticated (e.g., via SASL or PASS)
 * @property modes Set of user mode flags (i=invisible, o=operator, w=wallops)
 * @property awayMessage Away message if user is away, null otherwise
 * @property enabledCaps Set of IRCv3 capability names negotiated by this client (e.g. `message-tags`)
 *
 * @see IRCChannel for channel representation
 */
data class IRCUser(
    var nickname: String? = null,
    var username: String? = null,
    var realname: String? = null,
    var hostname: String,
    val channels: MutableSet<String> = mutableSetOf(),
    var registered: Boolean = false,
    var password: String? = null,
    var authenticated: Boolean = false,
    var accountName: String? = null,
    var modes: MutableSet<Char> = mutableSetOf(),
    var awayMessage: String? = null,
    val enabledCaps: MutableSet<String> = mutableSetOf(),
) {
    /**
     * Returns the full user mask in nick!user@host format.
     *
     * This format is used in IRC message prefixes to identify the source of messages.
     *
     * @return String in format "nickname!username@hostname"
     */
    fun fullMask(): String = "$nickname!$username@$hostname"

    /**
     * Checks if the user has completed registration.
     *
     * A user is considered fully registered if they have:
     * - Set a nickname via NICK command
     * - Provided user info via USER command
     * - Completed the registration sequence
     *
     * @return true if user is registered and has nickname and username
     */
    fun isRegistered(): Boolean = registered && nickname != null && username != null

    /**
     * Checks if the user is a server operator (+o mode).
     *
     * Server operators have elevated privileges including the ability to:
     * - Use OPER-only commands
     * - Override certain channel restrictions
     * - Grant channel operator status
     *
     * @return true if user has +o mode
     */
    fun isOperator(): Boolean = modes.contains('o')

    /**
     * Checks if the user is currently away.
     *
     * @return true if user has set an away message via AWAY command
     */
    fun isAway(): Boolean = awayMessage != null
}

/**
 * Represents an IRC channel on the server.
 *
 * Channels are the primary means of group communication in IRC. This class tracks
 * all channel state including membership, topic, modes, and access control lists.
 *
 * ## Channel Modes
 * - **m** (moderated): Only operators and voiced users can speak
 * - **s** (secret): Channel is hidden from LIST unless user is a member
 * - **i** (invite-only): Users must be invited to join
 * - **t** (topic lock): Only operators can change the topic
 * - **n** (no external): Only channel members can send messages
 * - **l** (user limit): Maximum number of users (stored in [userLimit])
 * - **k** (key): Channel requires password to join (stored in [key])
 *
 * ## User Status
 * - **Operator** (@): Can change modes, kick users, set topic (in [operators])
 * - **Voice** (+): Can speak in moderated channel (in [voiced])
 *
 * @property name Channel name (must start with #)
 * @property users Map of nickname to IRCUser for all users in the channel
 * @property topic Channel topic string, or null if no topic set
 * @property modes Set of active channel mode flags
 * @property operators Set of nicknames with channel operator status (@)
 * @property voiced Set of nicknames with voice status (+)
 * @property banList Set of ban masks (nick!user@host patterns with wildcards)
 * @property inviteList Set of nicknames invited to join (for +i channels)
 * @property key Channel password for +k mode, or null
 * @property userLimit Maximum users for +l mode, or null for unlimited
 *
 * @see IRCUser for user representation
 * @see docs/MODES.md for detailed mode documentation
 */
data class IRCChannel(
    val name: String,
    val users: MutableMap<String, IRCUser> = mutableMapOf(),
    var topic: String? = null,
    val modes: MutableSet<Char> = mutableSetOf(),
    val operators: MutableSet<String> = mutableSetOf(),
    val voiced: MutableSet<String> = mutableSetOf(),
    val banList: MutableSet<String> = mutableSetOf(),
    val inviteList: MutableSet<String> = mutableSetOf(),
    var key: String? = null,
    var userLimit: Int? = null,
) {
    /**
     * Checks if a user is a channel operator.
     *
     * Channel operators have permissions to:
     * - Change channel modes
     * - Kick users
     * - Grant operator/voice status
     * - Change topic (always, regardless of +t mode)
     * - Invite users to +i channels
     *
     * @param nickname The nickname to check
     * @return true if the user is in the operators set
     */
    fun isOperator(nickname: String): Boolean = operators.contains(nickname)

    /**
     * Checks if a user has voice status.
     *
     * Voiced users can speak in moderated (+m) channels.
     *
     * @param nickname The nickname to check
     * @return true if the user is in the voiced set
     */
    fun isVoiced(nickname: String): Boolean = voiced.contains(nickname)

    /**
     * Checks if a user can send messages to the channel.
     *
     * Message permissions depend on moderated (+m) mode:
     * - If channel is not moderated: all users can speak
     * - If channel is moderated: only operators and voiced users can speak
     *
     * @param nickname The nickname to check
     * @return true if the user is allowed to send PRIVMSG to this channel
     */
    fun canSpeak(nickname: String): Boolean {
        if (!modes.contains('m')) return true // Not moderated
        return isOperator(nickname) || isVoiced(nickname)
    }

    /**
     * Checks if a user mask matches any ban in the ban list.
     *
     * Ban masks use wildcards (* and ?) and are matched case-insensitively.
     * Common ban patterns:
     * - `*!*@*.example.com` - Ban entire domain
     * - `badnick!*@*` - Ban specific nickname
     * - `*!baduser@*` - Ban specific username
     *
     * @param userMask The user mask to check (nick!user@host format)
     * @return true if the user mask matches any ban mask in the ban list
     */
    fun isBanned(userMask: String): Boolean = banList.any { mask -> matchesMask(userMask, mask) }

    /**
     * Checks if a user mask matches a ban mask pattern.
     *
     * Supports wildcard matching:
     * - `*` matches zero or more characters
     * - `?` matches exactly one character
     * - Matching is case-insensitive
     *
     * @param userMask The user mask to test (nick!user@host)
     * @param banMask The ban pattern with optional wildcards
     * @return true if the user mask matches the ban pattern
     */
    private fun matchesMask(
        userMask: String,
        banMask: String,
    ): Boolean {
        val regex =
            banMask
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")
                .toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(userMask)
    }
}
