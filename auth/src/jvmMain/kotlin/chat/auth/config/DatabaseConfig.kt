package chat.auth.config

import chat.auth.model.AccountTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Object responsible for configuring and connecting to the database.
 */
object DatabaseConfig {
    /**
     * Connects to the PostgreSQL database and creates the necessary tables if they do not exist.
     */
    fun connect() {
        // Connect to the PostgreSQL database using the provided URL, driver, username, and password.
        Database.connect(
            url = "jdbc:postgresql://irc.malefic.xyz:17/irc",
            driver = "org.postgresql.Driver",
            user = "malefic",
            password = "hidden1!",
        )
        // Create the AccountTable if it does not already exist.
        transaction {
            SchemaUtils.create(AccountTable)
        }
    }
}
