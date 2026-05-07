package org.mochios.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.mochios.android.auth.SessionManager

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Neutral99,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = NeutralVariant40,
    onSecondary = Neutral99,
    secondaryContainer = NeutralVariant90,
    onSecondaryContainer = NeutralVariant20,
    tertiary = Blue50,
    onTertiary = Neutral99,
    tertiaryContainer = Blue95,
    onTertiaryContainer = Blue20,
    error = ErrorRed,
    onError = Neutral99,
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRed,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = NeutralVariant80,
    onSecondary = NeutralVariant20,
    secondaryContainer = NeutralVariant30,
    onSecondaryContainer = NeutralVariant90,
    tertiary = Blue70,
    onTertiary = Blue20,
    tertiaryContainer = Blue30,
    onTertiaryContainer = Blue90,
    error = ErrorRedLight,
    onError = ErrorRed,
    errorContainer = ErrorRed,
    onErrorContainer = ErrorRedLight,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30
)

@Composable
fun MochiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeAnchors: SessionManager.ThemeAnchors? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Server theme takes priority when available
        themeAnchors != null -> {
            ColorSchemeGenerator.generate(themeAnchors.hue, themeAnchors.chroma, darkTheme)
        }
        // Android 12+ dynamic color from wallpaper
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Fallback to hardcoded blue
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MochiTypography,
        content = content
    )
}
