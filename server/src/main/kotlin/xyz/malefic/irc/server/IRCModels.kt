package xyz.malefic.irc.server

import xyz.malefic.irc.protocol.IRCMessage

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
    var awayMessage: String? = null
) {
    fun fullMask(): String = "$nickname!$username@$hostname"
    
    fun isRegistered(): Boolean = registered && nickname != null && username != null
    
    fun isOperator(): Boolean = modes.contains('o')
    
    fun isAway(): Boolean = awayMessage != null
}

data class IRCChannel(
    val name: String,
    val users: MutableMap<String, IRCUser> = mutableMapOf(),
    var topic: String? = null,
    val modes: MutableSet<Char> = mutableSetOf()
) {
    fun broadcast(message: IRCMessage, except: String? = null) {
        users.values.forEach { user ->
            if (user.nickname != except) {
                // This will be handled by the connection manager
            }
        }
    }
}
