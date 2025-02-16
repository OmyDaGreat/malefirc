package chat.auth.api.account

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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

@Api
fun create(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) return
    val account = Json.decodeFromString(Account.serializer(), ctx.req.readBodyText()!!)

    val result =
        transaction {
            val exists = AccountTable.select(AccountTable.username eq account.username).count() > 0
            if (!exists) {
                AccountTable.insert {
                    it[username] = account.username
                    it[password] = account.password // TODO: Hash the password
                }
            }
            CreateAccountResponse(!exists)
        }

    ctx.res.setBodyText(Json.encodeToString(result))
}
