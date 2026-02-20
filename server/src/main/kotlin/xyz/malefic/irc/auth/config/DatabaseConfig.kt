package xyz.malefic.irc.auth.config

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountTable
import xyz.malefic.irc.server.history.MessageHistoryTable

/**
 * Configures and establishes the PostgreSQL database connection used by the IRC server.
 *
 * Connection parameters are read from environment variables with sensible defaults for
 * local development. Tables are created automatically on first connect if they do not exist.
 *
 * ## Environment Variables
 * | Variable | Default | Description |
 * |---|---|---|
 * | `DB_HOST` | `localhost` | PostgreSQL host |
 * | `DB_PORT` | `5432` | PostgreSQL port |
 * | `DB_NAME` | `malefirc` | Database name |
 * | `DB_USER` | `malefirc` | Database username |
 * | `DB_PASSWORD` | `malefirc` | Database password |
 *
 * @see AccountTable for the accounts schema
 * @see MessageHistoryTable for the message history schema
 */
object DatabaseConfig {
    /**
     * Connects to the PostgreSQL database and creates all required tables.
     *
     * Uses Exposed's [SchemaUtils.create] which is idempotent — existing tables are
     * left untouched.
     *
     * @throws Exception if the JDBC connection cannot be established.
     */
    fun connect() {
        val dbHost = System.getenv("DB_HOST") ?: "localhost"
        val dbPort = System.getenv("DB_PORT") ?: "5432"
        val dbName = System.getenv("DB_NAME") ?: "malefirc"
        val dbUser = System.getenv("DB_USER") ?: "malefirc"
        val dbPassword = System.getenv("DB_PASSWORD") ?: "malefirc"
        
        val jdbcUrl = "jdbc:postgresql://$dbHost:$dbPort/$dbName"
        
        println("Connecting to database: $jdbcUrl")
        
        Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = dbUser,
            password = dbPassword,
        )
        transaction {
            SchemaUtils.create(AccountTable, MessageHistoryTable)
        }
        
        println("Database connected and tables created")
    }
}
