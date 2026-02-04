package xyz.malefic.irc.protocol

/**
 * Helper functions to create common IRC messages
 */
object IRCMessageBuilder {
    fun welcome(serverName: String, nickname: String): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_WELCOME,
            params = listOf(nickname),
            trailing = "Welcome to the Internet Relay Network $nickname"
        )
    
    fun yourHost(serverName: String, nickname: String, version: String): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_YOURHOST,
            params = listOf(nickname),
            trailing = "Your host is $serverName, running version $version"
        )
    
    fun created(serverName: String, nickname: String, date: String): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_CREATED,
            params = listOf(nickname),
            trailing = "This server was created $date"
        )
    
    fun myInfo(serverName: String, nickname: String, version: String): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_MYINFO,
            params = listOf(nickname, serverName, version, "o", "o")
        )
    
    fun privmsg(from: String, target: String, message: String): IRCMessage =
        IRCMessage(
            prefix = from,
            command = IRCCommand.PRIVMSG,
            params = listOf(target),
            trailing = message
        )
    
    fun notice(from: String, target: String, message: String): IRCMessage =
        IRCMessage(
            prefix = from,
            command = IRCCommand.NOTICE,
            params = listOf(target),
            trailing = message
        )
    
    fun join(user: String, channel: String): IRCMessage =
        IRCMessage(
            prefix = user,
            command = IRCCommand.JOIN,
            params = listOf(channel)
        )
    
    fun part(user: String, channel: String, reason: String? = null): IRCMessage =
        IRCMessage(
            prefix = user,
            command = IRCCommand.PART,
            params = listOf(channel),
            trailing = reason
        )
    
    fun quit(user: String, reason: String? = null): IRCMessage =
        IRCMessage(
            prefix = user,
            command = IRCCommand.QUIT,
            trailing = reason ?: "Client Quit"
        )
    
    fun ping(server: String): IRCMessage =
        IRCMessage(
            command = IRCCommand.PING,
            params = listOf(server)
        )
    
    fun pong(server: String, target: String): IRCMessage =
        IRCMessage(
            prefix = server,
            command = IRCCommand.PONG,
            params = listOf(server, target)
        )
    
    fun topic(serverName: String, nickname: String, channel: String, topic: String): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_TOPIC,
            params = listOf(nickname, channel),
            trailing = topic
        )
    
    fun noTopic(serverName: String, nickname: String, channel: String): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_NOTOPIC,
            params = listOf(nickname, channel),
            trailing = "No topic is set"
        )
    
    fun nameReply(serverName: String, nickname: String, channel: String, names: List<String>): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_NAMREPLY,
            params = listOf(nickname, "=", channel),
            trailing = names.joinToString(" ")
        )
    
    fun endOfNames(serverName: String, nickname: String, channel: String): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = IRCReply.RPL_ENDOFNAMES,
            params = listOf(nickname, channel),
            trailing = "End of /NAMES list"
        )
    
    fun error(message: String): IRCMessage =
        IRCMessage(
            command = IRCCommand.ERROR,
            trailing = message
        )
    
    fun numericError(serverName: String, nickname: String, code: String, message: String): IRCMessage =
        IRCMessage(
            prefix = serverName,
            command = code,
            params = listOf(nickname),
            trailing = message
        )
}
