package xyz.malefic.irc.auth.config

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountTable
import xyz.malefic.irc.server.history.MessageHistoryTable

/**
 * Object responsible for configuring and connecting to the database.
 */
object DatabaseConfig {
    /**
     * Connects to the PostgreSQL database and creates the necessary tables if they do not exist.
     * Uses environment variables for configuration with sensible defaults.
     */
    fun connect() {
        val dbHost = System.getenv("DB_HOST") ?: "localhost"
        val dbPort = System.getenv("DB_PORT") ?: "5432"
        val dbName = System.getenv("DB_NAME") ?: "malefirc"
        val dbUser = System.getenv("DB_USER") ?: "malefirc"
        val dbPassword = System.getenv("DB_PASSWORD") ?: "malefirc"
        
        val jdbcUrl = "jdbc:postgresql://$dbHost:$dbPort/$dbName"
        
        println("Connecting to database: $jdbcUrl")
        
        // Connect to the PostgreSQL database using the provided URL, driver, username, and password.
        Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = dbUser,
            password = dbPassword,
        )
        // Create tables if they do not already exist.
        transaction {
            SchemaUtils.create(AccountTable, MessageHistoryTable)
        }
        
        println("Database connected and tables created")
    }
}
