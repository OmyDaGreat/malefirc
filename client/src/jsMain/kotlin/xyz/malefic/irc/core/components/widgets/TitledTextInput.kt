package xyz.malefic.irc.core.components.widgets

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.dom.ElementRefScope
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.px
import org.w3c.dom.HTMLInputElement
import xyz.malefic.irc.core.G

/** Silk CSS style for the grey label text above each [TitledTextInput]. */
val TitledTextInputLabelStyle =
    CssStyle.base {
        Modifier
            .fontSize(G.Ui.Text.Small)
            .color(Colors.Grey)
    }

/** Silk CSS style for the [TitledTextInput] wrapper column — sets width and bottom margin. */
val TitledTextInputStyle =
    CssStyle.base {
        Modifier
            .width(G.Ui.Width.Medium)
            .margin(bottom = 10.px)
    }

/**
 * A text input field with a descriptive label rendered above it.
 *
 * @param title Label text shown above the input.
 * @param text Current value of the input.
 * @param onTextChange Callback invoked whenever the input value changes.
 * @param masked If `true`, renders the input as a password field.
 * @param onCommit Callback invoked when the user presses Enter.
 * @param ref Optional DOM element reference for focus management.
 */
@Composable
fun TitledTextInput(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    masked: Boolean = false,
    onCommit: () -> Unit = {},
    ref: ElementRefScope<HTMLInputElement>? = null,
) {
    Column {
        SpanText(title, TitledTextInputLabelStyle.toModifier())
        TextInput(text, onTextChange, TitledTextInputStyle.toModifier(), password = masked, onCommit = onCommit, ref = ref)
    }
}
