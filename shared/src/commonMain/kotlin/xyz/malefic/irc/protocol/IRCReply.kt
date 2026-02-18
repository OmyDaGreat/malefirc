package xyz.malefic.irc.protocol

/**
 * IRC numeric reply codes as defined in RFC 1459 and RFC 2812.
 *
 * Numeric replies are three-digit codes sent by the server to clients as responses
 * to commands or to indicate various states and errors. Codes are organized into
 * ranges by function:
 * - 001-099: Connection and registration
 * - 200-399: Command responses and information
 * - 400-599: Error messages
 * - 900-999: Extensions (SASL, etc.)
 *
 * ## Usage
 * ```kotlin
 * sendMessage(
 *     connection,
 *     IRCMessageBuilder.numericError(
 *         serverName,
 *         nickname,
 *         IRCReply.ERR_NOSUCHNICK,
 *         "$target :No such nick/channel"
 *     )
 * )
 * ```
 *
 * @see IRCCommand for command constants
 * @see IRCMessageBuilder for building reply messages
 */
object IRCReply {
    // ==================== Welcome Messages (001-005) ====================
    
    /** 001 - Welcome to the IRC network */
    const val RPL_WELCOME = "001"
    
    /** 002 - Your host is <servername>, running version <version> */
    const val RPL_YOURHOST = "002"
    
    /** 003 - This server was created <date> */
    const val RPL_CREATED = "003"
    
    /** 004 - Server name, version, user modes, channel modes */
    const val RPL_MYINFO = "004"
    
    /** 005 - Server capabilities and supported features */
    const val RPL_BOUNCE = "005"
    
    // ==================== SASL Authentication (900-907) ====================
    
    /** 900 - You are now logged in as <account> */
    const val RPL_LOGGEDIN = "900"
    
    /** 901 - You are now logged out */
    const val RPL_LOGGEDOUT = "901"
    
    /** 903 - SASL authentication successful */
    const val RPL_SASLSUCCESS = "903"
    
    /** 904 - SASL authentication failed */
    const val RPL_SASLFAIL = "904"
    
    /** 905 - SASL message too long */
    const val RPL_SASLTOOLONG = "905"
    
    /** 906 - SASL authentication aborted */
    const val RPL_SASLABORTED = "906"
    
    /** 907 - You have already authenticated */
    const val RPL_SASLALREADY = "907"
    
    // ==================== User/Service Queries (301-306) ====================
    
    /** 302 - Userhost reply */
    const val RPL_USERHOST = "302"
    
    /** 303 - ISON reply with list of online users */
    const val RPL_ISON = "303"
    
    /** 301 - User is away: <away message> */
    const val RPL_AWAY = "301"
    
    /** 305 - You are no longer marked as being away */
    const val RPL_UNAWAY = "305"
    
    /** 306 - You have been marked as being away */
    const val RPL_NOWAWAY = "306"
    
    // ==================== WHOIS Replies (311-330) ====================
    
    /** 311 - <nick> <user> <host> * :<real name> */
    const val RPL_WHOISUSER = "311"
    
    /** 312 - <nick> <server> :<server info> */
    const val RPL_WHOISSERVER = "312"
    
    /** 313 - <nick> :is an IRC operator */
    const val RPL_WHOISOPERATOR = "313"
    
    /** 317 - <nick> <idle> :seconds idle */
    const val RPL_WHOISIDLE = "317"
    
    /** 318 - <nick> :End of WHOIS list */
    const val RPL_ENDOFWHOIS = "318"
    
    /** 319 - <nick> :<channels> */
    const val RPL_WHOISCHANNELS = "319"
    
    /** 330 - <nick> <account> :is logged in as */
    const val RPL_WHOISACCOUNT = "330"
    
    // ==================== LIST Replies (321-323) ====================
    
    /** 321 - Channel :Users  Name (list header) */
    const val RPL_LISTSTART = "321"
    
    /** 322 - <channel> <# visible> :<topic> */
    const val RPL_LIST = "322"
    
    /** 323 - :End of LIST */
    const val RPL_LISTEND = "323"
    
    // ==================== Channel Information (324-332) ====================
    
    /** 324 - <channel> <mode> <mode params> */
    const val RPL_CHANNELMODEIS = "324"
    
    /** 331 - <channel> :No topic is set */
    const val RPL_NOTOPIC = "331"
    
    /** 332 - <channel> :<topic> */
    const val RPL_TOPIC = "332"
    
    // ==================== NAMES Reply (353-366) ====================
    
    /** 353 - <channel> :[[@|+]<nick> [[@|+]<nick> [...]]] */
    const val RPL_NAMREPLY = "353"
    
    /** 366 - <channel> :End of NAMES list */
    const val RPL_ENDOFNAMES = "366"
    
    // ==================== Ban List (367-368) ====================
    
    /** 367 - <channel> <banmask> */
    const val RPL_BANLIST = "367"
    
    /** 368 - <channel> :End of channel ban list */
    const val RPL_ENDOFBANLIST = "368"
    
    // ==================== Invite List (346-347) ====================
    
    /** 346 - <channel> <invitemask> */
    const val RPL_INVITELIST = "346"
    
    /** 347 - <channel> :End of channel invite list */
    const val RPL_ENDOFINVITELIST = "347"
    
    // ==================== Invite/Mode (221, 341) ====================
    
    /** 341 - <nick> <channel> (invitation sent) */
    const val RPL_INVITING = "341"
    
    /** 221 - <user mode string> */
    const val RPL_UMODEIS = "221"
    
    // ==================== MOTD (372-376) ====================
    
    /** 375 - :- <server> Message of the day - */
    const val RPL_MOTDSTART = "375"
    
    /** 372 - :- <text> */
    const val RPL_MOTD = "372"
    
    /** 376 - :End of MOTD command */
    const val RPL_ENDOFMOTD = "376"
    
    // ==================== Error Messages (401-502) ====================
    
    /** 401 - <nickname> :No such nick/channel */
    const val ERR_NOSUCHNICK = "401"
    
    /** 402 - <server name> :No such server */
    const val ERR_NOSUCHSERVER = "402"
    
    /** 403 - <channel name> :No such channel */
    const val ERR_NOSUCHCHANNEL = "403"
    
    /** 404 - <channel name> :Cannot send to channel */
    const val ERR_CANNOTSENDTOCHAN = "404"
    
    /** 405 - <channel name> :You have joined too many channels */
    const val ERR_TOOMANYCHANNELS = "405"
    
    /** 406 - <nickname> :There was no such nickname */
    const val ERR_WASNOSUCHNICK = "406"
    
    /** 407 - <target> :<error code> recipients. <abort message> */
    const val ERR_TOOMANYTARGETS = "407"
    
    /** 409 - :No origin specified */
    const val ERR_NOORIGIN = "409"
    
    /** 411 - :No recipient given (<command>) */
    const val ERR_NORECIPIENT = "411"
    
    /** 412 - :No text to send */
    const val ERR_NOTEXTTOSEND = "412"
    
    /** 421 - <command> :Unknown command */
    const val ERR_UNKNOWNCOMMAND = "421"
    
    /** 422 - :MOTD File is missing */
    const val ERR_NOMOTD = "422"
    
    /** 431 - :No nickname given */
    const val ERR_NONICKNAMEGIVEN = "431"
    
    /** 432 - <nick> :Erroneous nickname */
    const val ERR_ERRONEUSNICKNAME = "432"
    
    /** 433 - <nick> :Nickname is already in use */
    const val ERR_NICKNAMEINUSE = "433"
    
    /** 436 - <nick> :Nickname collision KILL from <user>@<host> */
    const val ERR_NICKCOLLISION = "436"
    
    /** 441 - <nick> <channel> :They aren't on that channel */
    const val ERR_USERNOTINCHANNEL = "441"
    
    /** 442 - <channel> :You're not on that channel */
    const val ERR_NOTONCHANNEL = "442"
    
    /** 443 - <user> <channel> :is already on channel */
    const val ERR_USERONCHANNEL = "443"
    
    /** 451 - :You have not registered */
    const val ERR_NOTREGISTERED = "451"
    
    /** 461 - <command> :Not enough parameters */
    const val ERR_NEEDMOREPARAMS = "461"
    
    /** 462 - :Unauthorized command (already registered) */
    const val ERR_ALREADYREGISTERED = "462"
    
    /** 464 - :Password incorrect */
    const val ERR_PASSWDMISMATCH = "464"
    
    /** 471 - <channel> :Cannot join channel (+l) */
    const val ERR_CHANNELISFULL = "471"
    
    /** 472 - <char> :is unknown mode char to me for <channel> */
    const val ERR_UNKNOWNMODE = "472"
    
    /** 473 - <channel> :Cannot join channel (+i) */
    const val ERR_INVITEONLYCHAN = "473"
    
    /** 474 - <channel> :Cannot join channel (+b) */
    const val ERR_BANNEDFROMCHAN = "474"
    
    /** 475 - <channel> :Cannot join channel (+k) */
    const val ERR_BADCHANNELKEY = "475"
    
    /** 481 - :Permission Denied- You're not an IRC operator */
    const val ERR_NOPRIVILEGES = "481"
    
    /** 482 - <channel> :You're not channel operator */
    const val ERR_CHANOPRIVSNEEDED = "482"
    
    /** 501 - :Unknown MODE flag */
    const val ERR_UMODEUNKNOWNFLAG = "501"
    
    /** 502 - :Cannot change mode for other users */
    const val ERR_USERSDONTMATCH = "502"
}
