// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.Appearance
import org.mochios.android.i18n.Density
import org.mochios.android.i18n.FontPref
import org.mochios.android.i18n.FontSizePref
import org.mochios.android.i18n.Radius
import org.mochios.android.i18n.UserPreferences

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

/** Per-user corner radius (dp) for cards/buttons/dialogs. Themed surfaces
 *  that want to honour the user's preference read this CompositionLocal —
 *  defaults to 12dp (matches Material 3's medium). */
val LocalEntityRadius = compositionLocalOf { 12.dp }

/** Density multiplier applied by spacing-aware components. 1.0 = default. */
val LocalDensityScale = compositionLocalOf { 1.0f }

@Composable
fun MochiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeAnchors: SessionManager.ThemeAnchors? = null,
    preferences: UserPreferences? = null,
    content: @Composable () -> Unit
) {
    // Appearance overrides the system dark-theme setting.
    val isDark = when (preferences?.appearance) {
        Appearance.LIGHT -> false
        Appearance.DARK -> true
        Appearance.AUTO, null -> darkTheme
    }

    val colorScheme = when {
        // Server theme takes priority when available
        themeAnchors != null -> {
            ColorSchemeGenerator.generate(themeAnchors.hue, themeAnchors.chroma, isDark)
        }
        // Android 12+ dynamic color from wallpaper
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Fallback to hardcoded blue
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    // Typography: apply font + font_size preferences.
    val typography = remember(preferences?.font, preferences?.fontSize) {
        applyFontPreferences(MochiTypography, preferences?.font, preferences?.fontSize)
    }

    // Radius + density bridges. Cards / spacing-aware components read these
    // composition locals; passing null keeps the defaults (12dp / 1.0×).
    val radiusDp = when (preferences?.radius) {
        Radius.NONE -> 0
        Radius.SMALL -> 6
        Radius.MEDIUM -> 12
        Radius.LARGE -> 28
        Radius.THEME, null -> 12
    }
    val densityScale = preferences?.density?.scale ?: 1.0f

    CompositionLocalProvider(
        LocalEntityRadius provides radiusDp.dp,
        LocalDensityScale provides densityScale
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

private fun applyFontPreferences(
    base: Typography,
    font: FontPref?,
    fontSize: FontSizePref?
): Typography {
    val family = when (font) {
        // We don't ship a dyslexia-friendly font; SansSerif is the safest
        // fallback (clearer letterforms than Serif in most system fonts).
        FontPref.DYSLEXIA -> FontFamily.SansSerif
        FontPref.SERIF -> FontFamily.Serif
        FontPref.SYSTEM, FontPref.THEME, null -> FontFamily.Default
    }
    val scale = fontSize?.scale ?: 1.0f
    if (family == FontFamily.Default && scale == 1.0f) return base
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontSize = base.displayLarge.fontSize * scale, lineHeight = base.displayLarge.lineHeight * scale),
        displayMedium = base.displayMedium.copy(fontFamily = family, fontSize = base.displayMedium.fontSize * scale, lineHeight = base.displayMedium.lineHeight * scale),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontSize = base.displaySmall.fontSize * scale, lineHeight = base.displaySmall.lineHeight * scale),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontSize = base.headlineLarge.fontSize * scale, lineHeight = base.headlineLarge.lineHeight * scale),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontSize = base.headlineMedium.fontSize * scale, lineHeight = base.headlineMedium.lineHeight * scale),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontSize = base.headlineSmall.fontSize * scale, lineHeight = base.headlineSmall.lineHeight * scale),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontSize = base.titleLarge.fontSize * scale, lineHeight = base.titleLarge.lineHeight * scale),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontSize = base.titleMedium.fontSize * scale, lineHeight = base.titleMedium.lineHeight * scale),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontSize = base.titleSmall.fontSize * scale, lineHeight = base.titleSmall.lineHeight * scale),
        bodyLarge = base.bodyLarge.copy(fontFamily = family, fontSize = base.bodyLarge.fontSize * scale, lineHeight = base.bodyLarge.lineHeight * scale),
        bodyMedium = base.bodyMedium.copy(fontFamily = family, fontSize = base.bodyMedium.fontSize * scale, lineHeight = base.bodyMedium.lineHeight * scale),
        bodySmall = base.bodySmall.copy(fontFamily = family, fontSize = base.bodySmall.fontSize * scale, lineHeight = base.bodySmall.lineHeight * scale),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontSize = base.labelLarge.fontSize * scale, lineHeight = base.labelLarge.lineHeight * scale),
        labelMedium = base.labelMedium.copy(fontFamily = family, fontSize = base.labelMedium.fontSize * scale, lineHeight = base.labelMedium.lineHeight * scale),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontSize = base.labelSmall.fontSize * scale, lineHeight = base.labelSmall.lineHeight * scale),
    )
}
