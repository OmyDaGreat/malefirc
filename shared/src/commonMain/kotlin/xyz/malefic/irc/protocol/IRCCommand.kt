package xyz.malefic.irc.protocol

/**
 * IRC command constants as defined in RFC 1459 and RFC 2812.
 *
 * This object provides string constants for all supported IRC protocol commands.
 * Commands are organized into functional categories for easier navigation.
 *
 * ## Usage
 * ```kotlin
 * when (message.command) {
 *     IRCCommand.NICK -> handleNick(message)
 *     IRCCommand.JOIN -> handleJoin(message)
 *     // ...
 * }
 * ```
 *
 * @see IRCReply for numeric reply codes
 * @see IRCMessage for message parsing
 */
object IRCCommand {
    // Connection Registration
    /** Password authentication - must be sent before NICK/USER */
    const val PASS = "PASS"
    
    /** Set or change user's nickname */
    const val NICK = "NICK"
    
    /** Register user connection with username and real name */
    const val USER = "USER"
    
    /** Server-to-server connection (not typically used by clients) */
    const val SERVER = "SERVER"
    
    /** Authenticate as server operator */
    const val OPER = "OPER"
    
    /** Disconnect from server */
    const val QUIT = "QUIT"
    
    /** Capability negotiation (modern IRC extension) */
    const val CAP = "CAP"
    
    /** SASL authentication mechanism */
    const val AUTHENTICATE = "AUTHENTICATE"
    
    // Channel Operations
    /** Join one or more channels */
    const val JOIN = "JOIN"
    
    /** Leave a channel */
    const val PART = "PART"
    
    /** Query or change user/channel modes */
    const val MODE = "MODE"
    
    /** Query or set channel topic */
    const val TOPIC = "TOPIC"
    
    /** List users in a channel */
    const val NAMES = "NAMES"
    
    /** List all channels on the server */
    const val LIST = "LIST"
    
    /** Invite a user to a channel */
    const val INVITE = "INVITE"
    
    /** Forcibly remove a user from a channel */
    const val KICK = "KICK"
    
    // Server Queries
    /** Request message of the day */
    const val MOTD = "MOTD"
    
    /** Request server version information */
    const val VERSION = "VERSION"
    
    /** Request server administrator information */
    const val ADMIN = "ADMIN"
    
    /** Request server time */
    const val TIME = "TIME"
    
    /** Request server information */
    const val INFO = "INFO"
    
    // Sending Messages
    /** Send a message to user or channel */
    const val PRIVMSG = "PRIVMSG"
    
    /** Send a notice to user or channel (no auto-reply expected) */
    const val NOTICE = "NOTICE"
    
    // User-based Queries
    /** Query information about users matching a pattern */
    const val WHO = "WHO"
    
    /** Query detailed information about a user */
    const val WHOIS = "WHOIS"
    
    /** Query information about a user who has disconnected */
    const val WHOWAS = "WHOWAS"
    
    // Miscellaneous
    /** Forcibly disconnect a user (operator only) */
    const val KILL = "KILL"
    
    /** Server-to-client keepalive check */
    const val PING = "PING"
    
    /** Response to PING */
    const val PONG = "PONG"
    
    /** Error message (usually server-to-client) */
    const val ERROR = "ERROR"
    
    // Optional
    /** Set or unset away status */
    const val AWAY = "AWAY"
    
    /** Reload server configuration (operator only) */
    const val REHASH = "REHASH"
    
    /** Restart server (operator only) */
    const val RESTART = "RESTART"
    
    /** Summon a user to IRC (rarely implemented) */
    const val SUMMON = "SUMMON"
    
    /** List users logged into server host (rarely implemented) */
    const val USERS = "USERS"
    
    /** Query userhost information for nicknames */
    const val USERHOST = "USERHOST"
    
    /** Check if users are online */
    const val ISON = "ISON"
}
