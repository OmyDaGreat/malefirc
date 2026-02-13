package xyz.malefic.irc.protocol

/**
 * IRC numeric replies as defined in RFC 1459/2812
 */
object IRCReply {
    // Welcome messages
    const val RPL_WELCOME = "001"
    const val RPL_YOURHOST = "002"
    const val RPL_CREATED = "003"
    const val RPL_MYINFO = "004"
    const val RPL_BOUNCE = "005"
    
    // SASL
    const val RPL_LOGGEDIN = "900"
    const val RPL_LOGGEDOUT = "901"
    const val RPL_SASLSUCCESS = "903"
    const val RPL_SASLFAIL = "904"
    const val RPL_SASLTOOLONG = "905"
    const val RPL_SASLABORTED = "906"
    const val RPL_SASLALREADY = "907"
    
    // User/Service queries
    const val RPL_USERHOST = "302"
    const val RPL_ISON = "303"
    const val RPL_AWAY = "301"
    const val RPL_UNAWAY = "305"
    const val RPL_NOWAWAY = "306"
    
    // WHOIS replies
    const val RPL_WHOISUSER = "311"
    const val RPL_WHOISSERVER = "312"
    const val RPL_WHOISOPERATOR = "313"
    const val RPL_WHOISIDLE = "317"
    const val RPL_ENDOFWHOIS = "318"
    const val RPL_WHOISCHANNELS = "319"
    const val RPL_WHOISACCOUNT = "330"
    
    // LIST replies
    const val RPL_LISTSTART = "321"
    const val RPL_LIST = "322"
    const val RPL_LISTEND = "323"
    
    // Channel information
    const val RPL_CHANNELMODEIS = "324"
    const val RPL_NOTOPIC = "331"
    const val RPL_TOPIC = "332"
    
    // NAMES reply
    const val RPL_NAMREPLY = "353"
    const val RPL_ENDOFNAMES = "366"
    
    // MOTD
    const val RPL_MOTDSTART = "375"
    const val RPL_MOTD = "372"
    const val RPL_ENDOFMOTD = "376"
    
    // Errors
    const val ERR_NOSUCHNICK = "401"
    const val ERR_NOSUCHSERVER = "402"
    const val ERR_NOSUCHCHANNEL = "403"
    const val ERR_CANNOTSENDTOCHAN = "404"
    const val ERR_TOOMANYCHANNELS = "405"
    const val ERR_WASNOSUCHNICK = "406"
    const val ERR_TOOMANYTARGETS = "407"
    const val ERR_NOORIGIN = "409"
    const val ERR_NORECIPIENT = "411"
    const val ERR_NOTEXTTOSEND = "412"
    const val ERR_UNKNOWNCOMMAND = "421"
    const val ERR_NOMOTD = "422"
    const val ERR_NONICKNAMEGIVEN = "431"
    const val ERR_ERRONEUSNICKNAME = "432"
    const val ERR_NICKNAMEINUSE = "433"
    const val ERR_NICKCOLLISION = "436"
    const val ERR_USERNOTINCHANNEL = "441"
    const val ERR_NOTONCHANNEL = "442"
    const val ERR_USERONCHANNEL = "443"
    const val ERR_NOTREGISTERED = "451"
    const val ERR_NEEDMOREPARAMS = "461"
    const val ERR_ALREADYREGISTERED = "462"
    const val ERR_PASSWDMISMATCH = "464"
    const val ERR_CHANNELISFULL = "471"
    const val ERR_UNKNOWNMODE = "472"
    const val ERR_INVITEONLYCHAN = "473"
    const val ERR_BANNEDFROMCHAN = "474"
    const val ERR_BADCHANNELKEY = "475"
    const val ERR_NOPRIVILEGES = "481"
    const val ERR_CHANOPRIVSNEEDED = "482"
    const val ERR_UMODEUNKNOWNFLAG = "501"
    const val ERR_USERSDONTMATCH = "502"
}
