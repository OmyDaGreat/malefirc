package xyz.malefic.irc.auth.model

import com.varabyte.kobweb.api.data.add
import com.varabyte.kobweb.api.init.InitApi
import com.varabyte.kobweb.api.init.InitApiContext
import xyz.malefic.irc.auth.model.auth.Account

@InitApi
fun initAccounts(ctx: InitApiContext) {
    ctx.data.add(Accounts())
}

class Accounts {
    val set = mutableSetOf<Account>()
}
