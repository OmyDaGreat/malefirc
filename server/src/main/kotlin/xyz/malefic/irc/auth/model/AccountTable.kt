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
    val email = varchar("email", 255).nullable()
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val lastLogin = long("last_login").nullable()
    val isVerified = bool("is_verified").default(false)
}

class AccountEntity(
    id: EntityID<Int>,
) : IntEntity(id) {
    companion object : IntEntityClass<AccountEntity>(AccountTable)

    var username by AccountTable.username
    var password by AccountTable.password
    var email by AccountTable.email
    var createdAt by AccountTable.createdAt
    var lastLogin by AccountTable.lastLogin
    var isVerified by AccountTable.isVerified
}

@InitApi
fun initDatabase(ctx: InitApiContext) {
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
