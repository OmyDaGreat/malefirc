package xyz.malefic.irc.protocol

import kotlinx.serialization.Serializable

/**
 * Represents an IRC protocol message according to RFC 1459 and RFC 2812.
 *
 * IRC messages follow the format:
 * ```
 * [:prefix] <command> [params] [:trailing]
 * ```
 *
 * ## Message Components
 *
 * - **prefix**: Optional sender information (server name or user!ident@host)
 * - **command**: IRC command (e.g., "PRIVMSG", "JOIN") or numeric reply (e.g., "001")
 * - **params**: List of space-separated parameters
 * - **trailing**: Optional final parameter that may contain spaces (prefixed with ':')
 *
 * ## Examples
 *
 * Client to server:
 * ```
 * NICK alice
 * JOIN #channel
 * PRIVMSG #channel :Hello, world!
 * ```
 *
 * Server to client:
 * ```
 * :server.name 001 alice :Welcome to the IRC network
 * :alice!user@host PRIVMSG #channel :Hello, world!
 * :bob!user@host JOIN :#channel
 * ```
 *
 * ## Usage
 *
 * ### Parsing
 * ```kotlin
 * val message = IRCMessage.parse("PRIVMSG #test :Hello!")
 * // message.command = "PRIVMSG"
 * // message.params = ["#test"]
 * // message.trailing = "Hello!"
 * ```
 *
 * ### Creating
 * ```kotlin
 * val message = IRCMessage(
 *     prefix = "alice!user@host",
 *     command = "PRIVMSG",
 *     params = listOf("#channel"),
 *     trailing = "Hello, world!"
 * )
 * val wireFormat = message.toWireFormat() // ":alice!user@host PRIVMSG #channel :Hello, world!\r\n"
 * ```
 *
 * @property prefix Optional sender/source of the message (server or user mask)
 * @property command IRC command or numeric reply code
 * @property params List of space-separated parameters (cannot contain spaces)
 * @property trailing Optional final parameter that may contain spaces
 *
 * @see IRCCommand for command constants
 * @see IRCReply for numeric reply codes
 * @see IRCMessageBuilder for helper functions to create messages
 */
@Serializable
data class IRCMessage(
    val prefix: String? = null,
    val command: String,
    val params: List<String> = emptyList(),
    val trailing: String? = null
) {
    /**
     * Serializes this IRC message to wire format.
     *
     * The wire format follows the IRC protocol specification:
     * - Optional prefix prefixed with ':'
     * - Command
     * - Space-separated parameters
     * - Optional trailing parameter prefixed with ' :'
     * - Terminated with CRLF (\r\n)
     *
     * @return The message in IRC wire format, ready to send over the network
     *
     * @see parse for the inverse operation
     */
    fun toWireFormat(): String = buildString {
        // Add prefix if present
        prefix?.let { append(":$it ") }
        
        // Add command
        append(command)
        
        // Add parameters
        params.forEach { param ->
            append(" $param")
        }
        
        // Add trailing parameter if present
        trailing?.let { append(" :$it") }
        
        // IRC messages end with CRLF
        append("\r\n")
    }
    
    companion object {
        /**
         * Parses an IRC message from wire format.
         *
         * Handles the standard IRC message format:
         * ```
         * [:prefix] <command> [param1] [param2] [...] [:trailing]
         * ```
         *
         * - Prefix is optional and starts with ':'
         * - Command is required and can be alphabetic or numeric (e.g., "001")
         * - Parameters are space-separated
         * - Trailing parameter is optional, starts with ' :' and can contain spaces
         * - Line endings (CR, LF, CRLF) are automatically stripped
         *
         * @param line The raw IRC message line to parse
         * @return Parsed IRCMessage, or null if the line is invalid/malformed
         *
         * @see toWireFormat for the inverse operation
         */
        fun parse(line: String): IRCMessage? {
            if (line.isBlank()) return null
            
            var remaining = line.trimEnd('\r', '\n')
            var prefix: String? = null
            
            // Extract prefix if present
            if (remaining.startsWith(':')) {
                val spaceIdx = remaining.indexOf(' ')
                if (spaceIdx == -1) return null
                prefix = remaining.substring(1, spaceIdx)
                remaining = remaining.substring(spaceIdx + 1)
            }
            
            // Extract trailing parameter if present
            var trailing: String? = null
            val colonIdx = remaining.indexOf(" :")
            if (colonIdx != -1) {
                trailing = remaining.substring(colonIdx + 2)
                remaining = remaining.substring(0, colonIdx)
            }
            
            // Split command and parameters
            val parts = remaining.split(' ').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            
            val command = parts[0].uppercase()
            val params = parts.drop(1)
            
            return IRCMessage(prefix, command, params, trailing)
        }
    }
}
