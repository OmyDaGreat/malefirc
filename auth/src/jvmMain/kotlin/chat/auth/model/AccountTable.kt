package chat.auth.model

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Object representing the Account table in the database.
 * This table stores user account information.
 */
object AccountTable : IntIdTable() {
    /**
     * Column for storing the username.
     * It is a unique index with a maximum length of 50 characters.
     */
    val username = varchar("username", 50).uniqueIndex()

    /**
     * Column for storing the password.
     * It has a fixed length of 64 characters.
     */
    val password = varchar("password", 64)
}
