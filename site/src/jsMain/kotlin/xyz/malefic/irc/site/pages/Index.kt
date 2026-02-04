package xyz.malefic.irc.site.pages

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import xyz.malefic.irc.auth.model.auth.LoginState
import xyz.malefic.irc.core.components.layouts.PageLayout
import xyz.malefic.irc.core.components.sections.CenteredColumnContent
import xyz.malefic.irc.core.components.widgets.TextButton

@Page
@Composable
fun HomePage() =
    PageLayout("Malefirc") {
        val ctx = rememberPageContext()
        CenteredColumnContent {
            if (LoginState.current is LoginState.LoggedIn) {
                TextButton("Go to Chat") { ctx.router.navigateTo("/chat") }
            }
            TextButton("Create Account") { ctx.router.navigateTo("/account/create") }
            TextButton("Login") { ctx.router.navigateTo("/account/login") }
        }
    }
