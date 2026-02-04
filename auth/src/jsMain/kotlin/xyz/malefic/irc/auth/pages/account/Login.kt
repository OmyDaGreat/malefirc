package xyz.malefic.irc.auth.pages.account

import androidx.compose.runtime.*
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.UpdateHistoryMode
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import xyz.malefic.irc.auth.model.auth.Account
import xyz.malefic.irc.auth.model.auth.LoginState
import xyz.malefic.irc.core.components.layouts.PageLayout
import xyz.malefic.irc.core.components.sections.CenteredColumnContent
import xyz.malefic.irc.core.components.widgets.TextButton
import xyz.malefic.irc.core.components.widgets.TitledTextInput
import xyz.malefic.irc.core.styles.ErrorTextStyle

@Page
@Composable
fun LoginPage() {
    PageLayout("Login") {
        CenteredColumnContent {
            val ctx = rememberPageContext()
            val coroutine = rememberCoroutineScope()
            var username by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var errorText by remember { mutableStateOf("") }

            errorText =
                when {
                    username.any { it.isWhitespace() } -> "Username cannot contain whitespace."
                    password.any { it.isWhitespace() } -> "Password cannot contain whitespace."
                    else -> errorText
                }

            fun isValid() =
                username.isNotEmpty() &&
                    username.none { it.isWhitespace() } &&
                    password.isNotEmpty() &&
                    password.none { it.isWhitespace() }

            fun tryLogin() {
                if (!isValid()) return

                val account = Account(username, password)
                val accountBytes = Json.encodeToString(account).encodeToByteArray()
                coroutine.launch {
                    val response =
                        window.api
                            .post("/account/login", body = accountBytes)
                            .decodeToString()
                            .let {
                                Json.decodeFromString(
                                    xyz.malefic.irc.auth.model.auth.LoginResponse
                                        .serializer(),
                                    it,
                                )
                            }

                    if (response.succeeded) {
                        LoginState.current = LoginState.LoggedIn(account)
                        ctx.router.navigateTo("/chat", UpdateHistoryMode.REPLACE)
                    } else {
                        errorText = "Login failed. Invalid username / password?"
                    }
                }
            }

            TitledTextInput(
                "Username",
                username,
                {
                    errorText = ""
                    username = it
                },
                ref = ref { it.focus() },
                onCommit = ::tryLogin,
            )
            TitledTextInput(
                "Password",
                password,
                {
                    errorText = ""
                    password = it
                },
                masked = true,
                onCommit = ::tryLogin,
            )

            TextButton(
                "Login",
                enabled = isValid(),
                onClick = ::tryLogin,
            )

            if (errorText.isNotBlank()) {
                SpanText(errorText, ErrorTextStyle.toModifier())
            }
        }
    }
}
