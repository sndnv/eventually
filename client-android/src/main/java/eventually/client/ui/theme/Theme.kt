package eventually.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import eventually.client.R

@Composable
fun EventuallyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val primary = colorResource(R.color.primary)
    val secondary = colorResource(R.color.secondary)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            secondary = secondary,
            background = DarkBackground,
            surface = DarkBackground
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            background = Color.White,
            surface = Color.White
        )
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

private val DarkBackground = Color(0xFF121212)
