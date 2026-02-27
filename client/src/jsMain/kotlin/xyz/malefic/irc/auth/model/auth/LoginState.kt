package xyz.malefic.irc.auth.model.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.varabyte.kobweb.navigation.Router
import com.varabyte.kobweb.silk.components.icons.fa.FaRightFromBracket
import xyz.malefic.irc.core.components.sections.ExtraNavHeaderAction
import xyz.malefic.irc.core.components.sections.NavHeaderAction

/**
 * Application-wide login state, held as Compose observable state.
 *
 * Access the current state via [current].  Setting [current] also manages the
 * [ExtraNavHeaderAction] — a logout button is injected when the user is logged in
 * and removed when they log out.
 *
 * @see LoggedOut for the unauthenticated singleton
 * @see LoggedIn for the authenticated state carrying the account
 */
sealed class LoginState {
    companion object {
        private val mutableLoginState by lazy { mutableStateOf<LoginState>(LoggedOut) }

        /**
         * The current login state.  Compose will recompose any composable that reads
         * this property whenever it changes.
         */
        var current
            get() = mutableLoginState.value
            set(value) {
                mutableLoginState.value = value

                when (value) {
                    is LoggedIn -> {
                        ExtraNavHeaderAction.current =
                            object : NavHeaderAction() {
                                @Composable
                                override fun render() {
                                    FaRightFromBracket()
                                }

                                override fun onActionClicked(router: Router) {
                                    current = LoggedOut
                                    router.navigateTo("/")
                                }
                            }
                    }

                    LoggedOut -> {
                        ExtraNavHeaderAction.current = null
                    }
                }
            }
    }

    /** Unauthenticated state — shown on the home page before login. */
    object LoggedOut : LoginState()

    /**
     * Authenticated state carrying the user's account credentials.
     *
     * @property account The account returned by the login API.
     */
    class LoggedIn(
        val account: Account,
    ) : LoginState()
}
