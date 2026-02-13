package xyz.malefic.irc.protocol

/**
 * IRC commands as defined in RFC 1459/2812
 */
object IRCCommand {
    // Connection Registration
    const val PASS = "PASS"
    const val NICK = "NICK"
    const val USER = "USER"
    const val SERVER = "SERVER"
    const val OPER = "OPER"
    const val QUIT = "QUIT"
    const val CAP = "CAP"
    const val AUTHENTICATE = "AUTHENTICATE"
    
    // Channel Operations
    const val JOIN = "JOIN"
    const val PART = "PART"
    const val MODE = "MODE"
    const val TOPIC = "TOPIC"
    const val NAMES = "NAMES"
    const val LIST = "LIST"
    const val INVITE = "INVITE"
    const val KICK = "KICK"
    
    // Server Queries
    const val MOTD = "MOTD"
    const val VERSION = "VERSION"
    const val ADMIN = "ADMIN"
    const val TIME = "TIME"
    const val INFO = "INFO"
    
    // Sending Messages
    const val PRIVMSG = "PRIVMSG"
    const val NOTICE = "NOTICE"
    
    // User-based Queries
    const val WHO = "WHO"
    const val WHOIS = "WHOIS"
    const val WHOWAS = "WHOWAS"
    
    // Miscellaneous
    const val KILL = "KILL"
    const val PING = "PING"
    const val PONG = "PONG"
    const val ERROR = "ERROR"
    
    // Optional
    const val AWAY = "AWAY"
    const val REHASH = "REHASH"
    const val RESTART = "RESTART"
    const val SUMMON = "SUMMON"
    const val USERS = "USERS"
    const val USERHOST = "USERHOST"
    const val ISON = "ISON"
}
