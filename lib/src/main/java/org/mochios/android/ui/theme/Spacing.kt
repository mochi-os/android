// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import org.mochios.android.i18n.Density

/**
 * The account's Density preference, as the factors the shared layout
 * primitives set their padding, gaps and control heights by.
 *
 * Mirrors the web, where each preset (core's `style_preset_vars`) sets the
 * spacing base, the control heights and a card's padding, and nothing else:
 * text, icons, images and borders keep their size. Each factor is the
 * preset's value over the comfortable preset's, which every shipped theme
 * uses and the Android layout is drawn at. It never touches `LocalDensity`,
 * through which text would scale as well.
 *
 * @property gap Padding and gaps: the web's `--spacing`.
 * @property control Control heights: `--control-height-md` and `--input-h`.
 * @property horizontal A card's padding at its sides: `--card-px`.
 * @property vertical A card's padding at its top and bottom: `--card-py`.
 */
@Immutable
data class Spacing(
    val gap: Float,
    val control: Float,
    val horizontal: Float,
    val vertical: Float,
) {
    /** [value], a padding or a gap, at this density. */
    fun gap(value: Dp): Dp = value * gap

    /** [value], a control's height, at this density. */
    fun control(value: Dp): Dp = value * control

    /** A card's inner padding of [horizontal] and [vertical] at this density. */
    fun card(horizontal: Dp, vertical: Dp): PaddingValues =
        PaddingValues(horizontal = horizontal * this.horizontal, vertical = vertical * this.vertical)

    companion object {
        // The comfortable preset's values, in rem, that the others are measured against.
        private const val BASE = 0.27f
        private const val CONTROL = 2.25f
        private const val SIDES = 1.5f
        private const val ENDS = 1f

        val COMFORTABLE = Spacing(gap = 1f, control = 1f, horizontal = 1f, vertical = 1f)

        val COMPACT = Spacing(
            gap = 0.215f / BASE,
            control = 2f / CONTROL,
            horizontal = 1.25f / SIDES,
            vertical = 0.875f / ENDS,
        )

        val SPACIOUS = Spacing(
            gap = 0.285f / BASE,
            control = 2.5f / CONTROL,
            horizontal = 1.75f / SIDES,
            vertical = 1.25f / ENDS,
        )

        /**
         * The spacing [density] asks for. The theme's own choice does not reach
         * the phone, and every shipped theme chooses comfortable, so the theme
         * default is that.
         */
        fun of(density: Density?): Spacing = when (density) {
            Density.COMPACT -> COMPACT
            Density.SPACIOUS -> SPACIOUS
            Density.COMFORTABLE, Density.THEME, null -> COMFORTABLE
        }
    }
}

/** The [Spacing] the shared layout primitives read; [MochiTheme] provides it from the Density preference. */
val LocalSpacing = staticCompositionLocalOf { Spacing.COMFORTABLE }

/** [padding], every side of it a gap, at the reader's density. */
@Composable
@ReadOnlyComposable
fun spaced(padding: PaddingValues): PaddingValues {
    val spacing = LocalSpacing.current
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = spacing.gap(padding.calculateStartPadding(direction)),
        top = spacing.gap(padding.calculateTopPadding()),
        end = spacing.gap(padding.calculateEndPadding(direction)),
        bottom = spacing.gap(padding.calculateBottomPadding()),
    )
}
