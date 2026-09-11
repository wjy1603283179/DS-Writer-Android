package app.dswriter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAccents = staticCompositionLocalOf { LightAccents }

/** Product-specific colors that Material 3 does not define. */
object DSTheme {
    val accents: DSAccentColors
        @Composable @ReadOnlyComposable get() = LocalAccents.current
}

@Composable
fun DSWriterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAccents provides if (darkTheme) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = DSTypography,
            shapes = DSShapes,
            content = content,
        )
    }
}
