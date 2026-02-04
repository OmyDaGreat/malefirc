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
import xyz.malefic.irc.core.components.widgets.TitledTextInput

@Page
@Composable
fun CreateAccountPage() {
    PageLayout("Create Account") {
        CenteredColumnContent {
            val ctx = rememberPageContext()
            val coroutine = rememberCoroutineScope()
            var username by remember { mutableStateOf("") }
            var password1 by remember { mutableStateOf("") }
            var password2 by remember { mutableStateOf("") }
            var errorText by remember { mutableStateOf("") }

            errorText =
                when {
                    username.any { it.isWhitespace() } -> "Username cannot contain whitespace."
                    password1.any { it.isWhitespace() } -> "Password cannot contain whitespace."
                    password1 != password2 -> "Passwords don't match."
                    else -> errorText
                }

            fun isValid() = username.isNotEmpty() && password1.isNotEmpty() && errorText.isEmpty()

            fun tryCreate() {
                if (!isValid()) return

                val account = Account(username, password1)
                val accountBytes = Json.encodeToString(account).encodeToByteArray()
                coroutine.launch {
                    val response =
                        window.api
                            .post("/account/create", body = accountBytes)
                            .decodeToString()
                            .let {
                                Json.decodeFromString(
                                    xyz.malefic.irc.auth.model.auth.CreateAccountResponse
                                        .serializer(),
                                    it,
                                )
                            }

                    if (response.succeeded) {
                        LoginState.current = LoginState.LoggedIn(account)
                        ctx.router.navigateTo("/chat", UpdateHistoryMode.REPLACE)
                    } else {
                        errorText = "Could not create account. Username is already taken."
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
                onCommit = ::tryCreate,
            )
            TitledTextInput(
                "Password",
                password1,
                {
                    errorText = ""
                    password1 = it
                },
                masked = true,
                onCommit = ::tryCreate,
            )
            TitledTextInput(
                "Confirm Password",
                password2,
                {
                    errorText = ""
                    password2 = it
                },
                masked = true,
                onCommit = ::tryCreate,
            )

            _root_ide_package_.xyz.malefic.irc.core.components.widgets.TextButton(
                "Create Account",
                enabled = isValid(),
                onClick = ::tryCreate,
            )

            if (errorText.isNotBlank()) {
                SpanText(
                    errorText,
                    _root_ide_package_.xyz.malefic.irc.core.styles.ErrorTextStyle
                        .toModifier(),
                )
            }
        }
    }
}
