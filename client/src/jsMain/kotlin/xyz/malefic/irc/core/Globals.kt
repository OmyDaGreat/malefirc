package xyz.malefic.irc.core

import org.jetbrains.compose.web.css.px

/**
 * Global design-system constants for the Malefirc web client.
 *
 * All UI dimensions and font sizes are defined here so that layout changes
 * can be made in one place.  Import `G.Ui.Width.*` or `G.Ui.Text.*` in
 * composables instead of hard-coding pixel values.
 */
object G {
    /** Top-level UI dimension token container. */
    object Ui {
        /** Predefined layout widths for inputs, panels, and containers. */
        object Width {
            val Small = 200.px
            val Medium = 400.px
            val Large = 600.px
        }

        /** Predefined font sizes for labels, body text, and headings. */
        object Text {
            val Small = 18.px
            val MediumSmall = 22.px
            val Medium = 28.px
            val Large = 38.px
        }
    }
}
