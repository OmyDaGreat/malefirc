package xyz.malefic.irc.core.components.layouts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.padding
import kotlinx.browser.document
import org.jetbrains.compose.web.css.px
import xyz.malefic.irc.core.components.sections.NavHeader

/**
 * Standard page scaffold that sets the browser tab [title], renders the [NavHeader],
 * and centres [content] in a padded column.
 *
 * @param title Browser tab title to set via `document.title`.
 * @param content Page-specific composable content rendered below the header.
 */
@Composable
fun PageLayout(
    title: String,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(title) {
        document.title = title
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NavHeader()
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 10.px, left = 50.px, right = 50.px),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}
