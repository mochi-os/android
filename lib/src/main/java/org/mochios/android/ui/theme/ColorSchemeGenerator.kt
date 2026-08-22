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
 * Builds a Material 3 ColorScheme from the server's OKLCH theme anchors, the
 * same space the web renders from. Accent roles take `--hue`; the neutrals take
 * `--hue-bg`, which a theme may set to a different angle.
 */
object ColorSchemeGenerator {

    /**
     * Generate a full Material 3 ColorScheme from theme anchors. [chroma] is
     * passed through as-is; [oklch] backs it off where sRGB cannot hold it.
     */
    fun generate(hue: Float, chroma: Float, hueBg: Float, isDark: Boolean): ColorScheme =
        if (isDark) darkScheme(hue, hueBg, chroma) else lightScheme(hue, hueBg, chroma)

    private fun lightScheme(hue: Float, hueBg: Float, chroma: Float): ColorScheme = lightColorScheme(
        primary = oklch(0.52f, chroma, hue),
        onPrimary = Color.White,
        primaryContainer = oklch(0.92f, chroma * 0.35f, hue),
        onPrimaryContainer = oklch(0.30f, chroma * 0.85f, hue),
        secondary = oklch(0.52f, chroma * 0.28f, hue),
        onSecondary = Color.White,
        secondaryContainer = oklch(0.93f, chroma * 0.14f, hue),
        onSecondaryContainer = oklch(0.28f, chroma * 0.30f, hue),
        tertiary = oklch(0.52f, chroma * 0.55f, hue + 60f),
        onTertiary = Color.White,
        tertiaryContainer = oklch(0.92f, chroma * 0.28f, hue + 60f),
        onTertiaryContainer = oklch(0.30f, chroma * 0.55f, hue + 60f),
        error = ErrorRed,
        onError = Color.White,
        errorContainer = ErrorRedLight,
        onErrorContainer = ErrorRedDeep,
        background = oklch(0.985f, chroma * 0.02f, hueBg),
        onBackground = oklch(0.22f, chroma * 0.05f, hueBg),
        surface = oklch(0.985f, chroma * 0.02f, hueBg),
        onSurface = oklch(0.22f, chroma * 0.05f, hueBg),
        surfaceVariant = oklch(0.94f, chroma * 0.05f, hueBg),
        onSurfaceVariant = oklch(0.45f, chroma * 0.08f, hueBg),
        outline = oklch(0.62f, chroma * 0.06f, hueBg),
        outlineVariant = oklch(0.87f, chroma * 0.04f, hueBg),
        // Container ramp. Left unset these fell back to Material's own
        // baseline greys, which are struck from a violet neutral - that is
        // why menus and sheets used to read lavender inside an otherwise
        // warm theme. Deriving them from hueBg like every other neutral
        // keeps one hue across the whole surface stack, and gives cards a
        // tone to separate against now that they carry no border.
        surfaceContainerLowest = oklch(1.0f, 0f, hueBg),
        surfaceContainerLow = oklch(0.972f, chroma * 0.03f, hueBg),
        surfaceContainer = oklch(0.958f, chroma * 0.04f, hueBg),
        surfaceContainerHigh = oklch(0.941f, chroma * 0.05f, hueBg),
        surfaceContainerHighest = oklch(0.925f, chroma * 0.06f, hueBg),
        surfaceBright = oklch(0.985f, chroma * 0.02f, hueBg),
        surfaceDim = oklch(0.895f, chroma * 0.05f, hueBg),
        inverseSurface = oklch(0.27f, chroma * 0.04f, hueBg),
        inverseOnSurface = oklch(0.96f, chroma * 0.02f, hueBg),
        inversePrimary = oklch(0.78f, chroma * 0.75f, hue),
        scrim = Color.Black,
        surfaceTint = oklch(0.52f, chroma, hue),
    )

    private fun darkScheme(hue: Float, hueBg: Float, chroma: Float): ColorScheme = darkColorScheme(
        primary = oklch(0.78f, chroma * 0.75f, hue),
        onPrimary = oklch(0.26f, chroma * 0.60f, hue),
        primaryContainer = oklch(0.38f, chroma * 0.70f, hue),
        onPrimaryContainer = oklch(0.90f, chroma * 0.30f, hue),
        secondary = oklch(0.78f, chroma * 0.22f, hue),
        onSecondary = oklch(0.26f, chroma * 0.20f, hue),
        secondaryContainer = oklch(0.34f, chroma * 0.20f, hue),
        onSecondaryContainer = oklch(0.90f, chroma * 0.15f, hue),
        tertiary = oklch(0.78f, chroma * 0.45f, hue + 60f),
        onTertiary = oklch(0.26f, chroma * 0.40f, hue + 60f),
        tertiaryContainer = oklch(0.38f, chroma * 0.40f, hue + 60f),
        onTertiaryContainer = oklch(0.90f, chroma * 0.25f, hue + 60f),
        error = ErrorRedDark,
        onError = ErrorRedOnDark,
        errorContainer = ErrorRedOnDark,
        onErrorContainer = ErrorRedLight,
        background = oklch(0.145f, chroma * 0.02f, hueBg),
        onBackground = oklch(0.92f, chroma * 0.03f, hueBg),
        surface = oklch(0.145f, chroma * 0.02f, hueBg),
        onSurface = oklch(0.92f, chroma * 0.03f, hueBg),
        surfaceVariant = oklch(0.255f, chroma * 0.03f, hueBg),
        onSurfaceVariant = oklch(0.78f, chroma * 0.04f, hueBg),
        outline = oklch(0.55f, chroma * 0.04f, hueBg),
        outlineVariant = oklch(0.32f, chroma * 0.03f, hueBg),
        // See the light scheme: the ramp climbs away from the background
        // instead of down, which is how a dark surface reads as raised.
        surfaceContainerLowest = oklch(0.105f, chroma * 0.02f, hueBg),
        surfaceContainerLow = oklch(0.175f, chroma * 0.025f, hueBg),
        surfaceContainer = oklch(0.205f, chroma * 0.03f, hueBg),
        surfaceContainerHigh = oklch(0.245f, chroma * 0.03f, hueBg),
        surfaceContainerHighest = oklch(0.29f, chroma * 0.035f, hueBg),
        surfaceBright = oklch(0.33f, chroma * 0.035f, hueBg),
        surfaceDim = oklch(0.145f, chroma * 0.02f, hueBg),
        inverseSurface = oklch(0.92f, chroma * 0.03f, hueBg),
        inverseOnSurface = oklch(0.22f, chroma * 0.03f, hueBg),
        inversePrimary = oklch(0.52f, chroma, hue),
        scrim = Color.Black,
        surfaceTint = oklch(0.78f, chroma * 0.75f, hue),
    )
}
