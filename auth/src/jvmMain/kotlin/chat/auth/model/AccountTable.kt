package chat.auth.model

import com.varabyte.kobweb.api.init.InitApi
import com.varabyte.kobweb.api.init.InitApiContext
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object AccountTable : IntIdTable("account") {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 128)
}

@InitApi
fun initDatabase(ctx: InitApiContext) {
    Database.connect(
        url = "jdbc:postgresql://irc.malefic.xyz:17/irc",
        driver = "org.postgresql.Driver",
        user = "malefic",
        password = "hidden1!",
    )

    transaction {
        SchemaUtils.create(AccountTable)
    }
}
