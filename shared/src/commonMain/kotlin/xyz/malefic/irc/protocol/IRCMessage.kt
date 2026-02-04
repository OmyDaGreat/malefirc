package xyz.malefic.irc.protocol

import kotlinx.serialization.Serializable

/**
 * Represents an IRC message according to RFC 1459/2812
 * Format: [:prefix] <command> [params] [:trailing]
 */
@Serializable
data class IRCMessage(
    val prefix: String? = null,
    val command: String,
    val params: List<String> = emptyList(),
    val trailing: String? = null
) {
    /**
     * Serialize IRC message to wire format
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
         * Parse an IRC message from wire format
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
