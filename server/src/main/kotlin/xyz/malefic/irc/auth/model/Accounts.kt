package xyz.malefic.irc.auth.model

import com.varabyte.kobweb.api.data.add
import com.varabyte.kobweb.api.init.InitApi
import com.varabyte.kobweb.api.init.InitApiContext
import xyz.malefic.irc.auth.model.auth.Account

/**
 * Initialises the in-memory [Accounts] store and registers it with the Kobweb API context.
 *
 * @param ctx Kobweb API initialisation context used to register shared data.
 */
@InitApi
fun initAccounts(ctx: InitApiContext) {
    ctx.data.add(Accounts())
}

/**
 * In-memory collection of authenticated IRC accounts for the web client session.
 *
 * Populated by the authentication API and queried by the chat page.
 */
class Accounts {
    /** Set of currently authenticated account credentials. */
    val set = mutableSetOf<Account>()
}
