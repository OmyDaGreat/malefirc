package xyz.malefic.irc.auth.util

import org.mindrot.jbcrypt.BCrypt

object PasswordHash {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

    fun verify(password: String, hashed: String): Boolean = BCrypt.checkpw(password, hashed)
}
