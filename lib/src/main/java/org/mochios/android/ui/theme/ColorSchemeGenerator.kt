// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Generates a Material 3 ColorScheme from OKLCH-style hue and chroma anchors.
 *
 * The web uses OKLCH (--hue, --hue-chroma, --hue-bg) to drive the entire palette.
 * This maps those values to HSL-based tonal palettes for Android. The hue mapping
 * is approximate (OKLCH and HSL hues are not identical) but close enough to produce
 * a visually consistent theme across platforms.
 */
object ColorSchemeGenerator {

    /**
     * Generate a full Material 3 ColorScheme from theme anchors.
     * @param hue OKLCH hue (0-360)
     * @param chroma OKLCH chroma (typically 0.05-0.30)
     * @param isDark whether to generate a dark color scheme
     */
    fun generate(hue: Float, chroma: Float, isDark: Boolean): ColorScheme {
        // Map OKLCH chroma to HSL saturation (OKLCH ~0.20 = fully saturated)
        val sat = (chroma / 0.20f).coerceIn(0.2f, 1f)

        return if (isDark) darkScheme(hue, sat) else lightScheme(hue, sat)
    }

    private fun lightScheme(hue: Float, sat: Float): ColorScheme = lightColorScheme(
        primary = hsl(hue, sat * 0.85f, 0.45f),
        onPrimary = Color.White,
        primaryContainer = hsl(hue, sat * 0.5f, 0.90f),
        onPrimaryContainer = hsl(hue, sat * 0.8f, 0.15f),
        secondary = hsl(hue, sat * 0.12f, 0.40f),
        onSecondary = Color.White,
        secondaryContainer = hsl(hue, sat * 0.10f, 0.92f),
        onSecondaryContainer = hsl(hue, sat * 0.10f, 0.15f),
        tertiary = hsl(hue + 60f, sat * 0.5f, 0.42f),
        onTertiary = Color.White,
        tertiaryContainer = hsl(hue + 60f, sat * 0.3f, 0.90f),
        onTertiaryContainer = hsl(hue + 60f, sat * 0.5f, 0.15f),
        error = ErrorRed,
        onError = Color.White,
        errorContainer = ErrorRedLight,
        onErrorContainer = ErrorRed,
        background = hsl(hue, sat * 0.02f, 0.98f),
        onBackground = hsl(hue, sat * 0.02f, 0.10f),
        surface = hsl(hue, sat * 0.02f, 0.98f),
        onSurface = hsl(hue, sat * 0.02f, 0.10f),
        surfaceVariant = hsl(hue, sat * 0.05f, 0.93f),
        onSurfaceVariant = hsl(hue, sat * 0.05f, 0.30f),
        outline = hsl(hue, sat * 0.05f, 0.50f),
        outlineVariant = hsl(hue, sat * 0.04f, 0.80f),
    )

    private fun darkScheme(hue: Float, sat: Float): ColorScheme = darkColorScheme(
        primary = hsl(hue, sat * 0.65f, 0.75f),
        onPrimary = hsl(hue, sat * 0.80f, 0.15f),
        primaryContainer = hsl(hue, sat * 0.60f, 0.25f),
        onPrimaryContainer = hsl(hue, sat * 0.45f, 0.88f),
        secondary = hsl(hue, sat * 0.10f, 0.75f),
        onSecondary = hsl(hue, sat * 0.08f, 0.15f),
        secondaryContainer = hsl(hue, sat * 0.10f, 0.22f),
        onSecondaryContainer = hsl(hue, sat * 0.08f, 0.88f),
        tertiary = hsl(hue + 60f, sat * 0.40f, 0.72f),
        onTertiary = hsl(hue + 60f, sat * 0.40f, 0.15f),
        tertiaryContainer = hsl(hue + 60f, sat * 0.30f, 0.25f),
        onTertiaryContainer = hsl(hue + 60f, sat * 0.30f, 0.88f),
        error = ErrorRedLight,
        onError = ErrorRed,
        errorContainer = ErrorRed,
        onErrorContainer = ErrorRedLight,
        background = hsl(hue, sat * 0.03f, 0.08f),
        onBackground = hsl(hue, sat * 0.02f, 0.90f),
        surface = hsl(hue, sat * 0.03f, 0.08f),
        onSurface = hsl(hue, sat * 0.02f, 0.90f),
        surfaceVariant = hsl(hue, sat * 0.04f, 0.15f),
        onSurfaceVariant = hsl(hue, sat * 0.04f, 0.80f),
        outline = hsl(hue, sat * 0.04f, 0.45f),
        outlineVariant = hsl(hue, sat * 0.04f, 0.25f),
    )

    private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
        return Color.hsl(hue.mod(360f), saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
    }
}
