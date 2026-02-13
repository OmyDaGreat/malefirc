package xyz.malefic.irc.auth.model

import com.varabyte.kobweb.api.init.InitApi
import com.varabyte.kobweb.api.init.InitApiContext
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object AccountTable : IntIdTable("account") {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 60)
}

class AccountEntity(
    id: EntityID<Int>,
) : IntEntity(id) {
    companion object : IntEntityClass<AccountEntity>(AccountTable)

    var username by AccountTable.username
    var password by AccountTable.password
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
