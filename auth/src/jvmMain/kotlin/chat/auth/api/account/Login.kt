package chat.auth.api.account

import chat.auth.model.AccountTable
import chat.auth.model.auth.Account
import chat.auth.model.auth.LoginResponse
import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import com.varabyte.kobweb.api.http.readBodyText
import com.varabyte.kobweb.api.http.setBodyText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

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
