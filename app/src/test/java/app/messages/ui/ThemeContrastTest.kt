package app.messages.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every palette, light and dark, keeps its text readable (WCAG AA, 4.5:1). */
class ThemeContrastTest {
    private fun schemes(): List<Pair<String, ColorScheme>> =
        ThemeChoices.flatMap { listOf("${it.id} light" to it.light, "${it.id} dark" to it.dark) }

    private fun assertReadable(
        name: String,
        pair: String,
        fg: Color,
        bg: Color,
    ) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$name $pair is %.2f:1".format(ratio), ratio >= 4.5f)
    }

    @Test
    fun textOnFilledColorsIsReadable() {
        for ((name, s) in schemes()) {
            assertReadable(name, "onPrimary/primary", s.onPrimary, s.primary)
            assertReadable(name, "onSecondary/secondary", s.onSecondary, s.secondary)
            assertReadable(name, "onError/error", s.onError, s.error)
            assertReadable(name, "onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
            assertReadable(name, "onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        }
    }

    @Test
    fun textOnPagesIsReadable() {
        for ((name, s) in schemes()) {
            for ((surface, color) in listOf("surface" to s.surface, "canvas" to s.mailCanvas())) {
                assertReadable(name, "onSurface/$surface", s.onSurface, color)
                assertReadable(name, "onSurfaceVariant/$surface", s.onSurfaceVariant, color)
                assertReadable(name, "primary/$surface", s.primary, color)
            }
        }
    }

    @Test
    fun surfaceContainersComeFromThePalette() {
        // Material's purple baseline must not leak into sheets, menus, or fields.
        for ((name, s) in schemes()) {
            listOf(s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest).forEach {
                assertTrue("$name surface container is far from the page", contrastRatio(it, s.surface) < 1.6f)
            }
        }
    }
}
