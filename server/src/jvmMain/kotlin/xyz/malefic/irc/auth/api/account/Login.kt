package xyz.malefic.irc.auth.api.account

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import com.varabyte.kobweb.api.http.readBodyText
import com.varabyte.kobweb.api.http.setBodyText
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.malefic.irc.auth.model.AccountTable
import xyz.malefic.irc.auth.model.auth.Account
import xyz.malefic.irc.auth.model.auth.LoginResponse

@Api
fun login(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) return
    val account = Json.decodeFromString(Account.serializer(), ctx.req.readBodyText()!!)

    val succeeded =
        transaction {
            AccountTable
                .select(
                    (AccountTable.username eq account.username) and (AccountTable.password eq account.password),
                ).count() > 0
        }

    ctx.res.setBodyText(Json.encodeToString(LoginResponse(succeeded)))
}
