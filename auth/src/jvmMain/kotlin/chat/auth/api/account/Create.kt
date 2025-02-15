package chat.auth.api.account

import chat.auth.config.DatabaseConfig
import chat.auth.model.AccountTable
import chat.auth.model.auth.Account
import chat.auth.model.auth.CreateAccountResponse
import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import com.varabyte.kobweb.api.http.readBodyText
import com.varabyte.kobweb.api.http.setBodyText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

@Api
fun create(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) return

    DatabaseConfig.connect()

    val account = Json.decodeFromString<Account>(ctx.req.readBodyText()!!)
    var result = CreateAccountResponse(succeeded = false)

    transaction {
        val existingAccount = AccountTable.select { AccountTable.username eq account.username }.singleOrNull()
        if (existingAccount == null) {
            AccountTable.insert {
                it[username] = account.username
                it[password] = account.password
            }
            result = CreateAccountResponse(succeeded = true)
        }
    }

    ctx.res.setBodyText(Json.encodeToString(result))
}
