package xyz.malefic.irc.auth.api.account

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.Body
import com.varabyte.kobweb.api.http.HttpMethod
import com.varabyte.kobweb.api.http.text
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountEntity
import xyz.malefic.irc.auth.model.AccountTable
import xyz.malefic.irc.auth.model.auth.Account
import xyz.malefic.irc.auth.model.auth.LoginResponse
import xyz.malefic.irc.auth.util.PasswordHash

@Api
suspend fun login(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) return
    val account = Json.decodeFromString(Account.serializer(), ctx.req.body?.text()!!)

    val succeeded =
        transaction {
            val accountEntity = AccountEntity.find { AccountTable.username eq account.username }.singleOrNull()

            accountEntity?.let {
                PasswordHash.verify(account.password, it.password)
            } ?: false
        }

    ctx.res.body = Body.text(Json.encodeToString(LoginResponse(succeeded)))
}
