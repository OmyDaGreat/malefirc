package xyz.malefic.irc.auth.components.sections

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import org.jetbrains.compose.web.dom.Text
import xyz.malefic.irc.core.components.sections.CenteredColumnContent
import xyz.malefic.irc.core.components.widgets.TextButton

@Composable
fun LoggedOutMessage() {
    CenteredColumnContent {
        val ctx = rememberPageContext()
        Text("You are not logged in and cannot access this page.")
        TextButton("Go Home") {
            ctx.router.navigateTo("/", UpdateHistoryMode.REPLACE)
        }
    }
}
