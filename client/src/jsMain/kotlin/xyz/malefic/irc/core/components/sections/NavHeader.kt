package xyz.malefic.irc.core.components.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.foundation.layout.Spacer
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.Router
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.icons.fa.FaHouse
import com.varabyte.kobweb.silk.components.icons.fa.FaMoon
import com.varabyte.kobweb.silk.components.icons.fa.FaQuestion
import com.varabyte.kobweb.silk.components.icons.fa.FaSun
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.common.SmoothColorStyle
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.background
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Text

/** Silk CSS style for the navigation bar background — inverts the page colour palette. */
val NavHeaderStyle =
    CssStyle.base(extraModifier = { SmoothColorStyle.toModifier() }) {
        Modifier
            .fillMaxWidth()
            .height(60.px)
            .padding(leftRight = 10.px, topBottom = 5.px)
            // Intentionally invert the header colors from the rest of the page
            .backgroundColor(colorMode.toPalette().color)
    }

/** Silk CSS style for the centred application title text in the nav bar. */
val TitleStyle =
    CssStyle.base {
        Modifier
            .fontSize(26.px)
            .fontWeight(FontWeight.Bold)
            // Intentionally invert the header colors from the rest of the page
            .color(colorMode.toPalette().background)
    }

/** Silk CSS style for circular icon buttons in the navigation bar. */
val NavButtonStyle =
    CssStyle.base {
        Modifier
            .margin(leftRight = 5.px)
            .padding(0.px)
            .size(40.px)
            .borderRadius(50.percent)
    }

/**
 * A circular icon button used inside the [NavHeader].
 *
 * @param onClick Action invoked when the button is pressed.
 * @param content Icon composable rendered inside the button.
 */
@Composable
private fun NavButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Button(onClick = { onClick() }, NavButtonStyle.toModifier(), content = { content() })
}

/**
 * Abstract descriptor for an action button that can be injected into the [NavHeader].
 *
 * Implement [render] to provide the button icon and [onActionClicked] to handle the press.
 * Set [ExtraNavHeaderAction.current] to show an instance or `null` to hide it.
 */
abstract class NavHeaderAction {
    @Composable
    abstract fun render()

    abstract fun onActionClicked(router: Router)
}

/**
 * Singleton holder for an optional extra action button in the [NavHeader].
 *
 * Set [current] to a [NavHeaderAction] instance to display an additional icon button
 * (e.g., a logout button when the user is logged in).  Set it to `null` to remove it.
 *
 * @see LoginState for how this is used to inject a logout action.
 */
object ExtraNavHeaderAction {
    private val mutableActionState by lazy { mutableStateOf<NavHeaderAction?>(null) }

    var current: NavHeaderAction?
        get() = mutableActionState.value
        set(value) {
            mutableActionState.value = value
        }
}

/**
 * Application navigation bar composable.
 *
 * Renders a fixed-height bar containing:
 * - Home and About navigation buttons (left side)
 * - An optional extra action from [ExtraNavHeaderAction] (e.g., logout)
 * - A light/dark colour-mode toggle button
 * - The application title centred over the bar
 */
@Composable
fun NavHeader() {
    val ctx = rememberPageContext()
    var colorMode by ColorMode.currentState
    Box(NavHeaderStyle.toModifier()) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton(onClick = { ctx.router.navigateTo("/") }) { FaHouse() }
            NavButton(onClick = { ctx.router.navigateTo("/about") }) { FaQuestion() }
            Spacer()

            val router = rememberPageContext().router
            ExtraNavHeaderAction.current?.let { extraAction ->
                NavButton(onClick = { extraAction.onActionClicked(router) }) {
                    extraAction.render()
                }
            }

            NavButton(onClick = { colorMode = colorMode.opposite }) {
                when (colorMode) {
                    ColorMode.LIGHT -> FaMoon()
                    ColorMode.DARK -> FaSun()
                }
            }
        }

        Box(TitleStyle.toModifier().align(Alignment.Center)) {
            Text("Malefirc \uD83D\uDCAC")
        }
    }
}
