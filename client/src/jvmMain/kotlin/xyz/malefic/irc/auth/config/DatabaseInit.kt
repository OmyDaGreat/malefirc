package xyz.malefic.irc.auth.config

import com.varabyte.kobweb.api.init.InitApi
import com.varabyte.kobweb.api.init.InitApiContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountTable

/**
 * Kobweb `@InitApi` hook that connects to PostgreSQL and creates the [AccountTable] schema
 * when the web client's server process starts.
 *
 * Connection parameters are read from the same environment variables used by the IRC server:
 *
 * | Variable | Default | Description |
 * |---|---|---|
 * | `DB_HOST` | `localhost` | PostgreSQL host |
 * | `DB_PORT` | `5432` | PostgreSQL port |
 * | `DB_NAME` | `malefirc` | Database name |
 * | `DB_USER` | `malefirc` | Database username |
 * | `DB_PASSWORD` | `malefirc` | Database password |
 *
 * @param ctx Kobweb API initialisation context (unused; required by the `@InitApi` contract).
 */
@InitApi
fun initDatabase(
    @Suppress("UNUSED_PARAMETER") ctx: InitApiContext,
) {
    val dbHost = System.getenv("DB_HOST") ?: "localhost"
    val dbPort = System.getenv("DB_PORT") ?: "5432"
    val dbName = System.getenv("DB_NAME") ?: "malefirc"
    val dbUser = System.getenv("DB_USER") ?: "malefirc"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "malefirc"

    Database.connect(
        url = "jdbc:postgresql://$dbHost:$dbPort/$dbName",
        driver = "org.postgresql.Driver",
        user = dbUser,
        password = dbPassword,
    )

    transaction {
        SchemaUtils.create(AccountTable)
    }
}
