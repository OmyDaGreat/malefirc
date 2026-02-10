package xyz.malefic.irc.auth.api.account

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import com.varabyte.kobweb.api.http.readBodyText
import com.varabyte.kobweb.api.http.setBodyText
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountTable
import xyz.malefic.irc.auth.model.auth.Account
import xyz.malefic.irc.auth.model.auth.CreateAccountResponse
import xyz.malefic.irc.auth.util.PasswordHash

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
                    it[password] = PasswordHash.hash(account.password)
                }
            }
            CreateAccountResponse(!exists)
        }

    ctx.res.setBodyText(Json.encodeToString(result))
}
