// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import org.mochios.android.ui.theme.LocalSeedPalette
import org.mochios.android.ui.theme.SeedPalette
import org.mochios.android.ui.theme.oklch

/**
 * The identity colour for an entity, harmonised with the current theme.
 *
 * Every entity that shows a colour-seeded circle — avatars, list-row icons,
 * drawer rows — comes through here, so the same id is the same colour
 * wherever it appears. Same seed and same theme always give the same colour;
 * change the theme and the whole set moves with it.
 *
 * The seed picks a slot on a fixed wheel of [SLOTS] positions spread across
 * [SPREAD] degrees either side of the theme's own hue, rather than anywhere
 * on the 360° circle. Staying inside that arc is what keeps a row of circles
 * looking like one family instead of a bag of highlighters, while the arc is
 * wide enough that neighbouring rows still separate. Alternating lightness
 * across the wheel doubles the distinguishable slots without widening it.
 */
private const val SLOTS = 12
private const val SPREAD = 55f

@Composable
fun seededEntityColor(seed: String): Color {
    val palette = LocalSeedPalette.current
    return remember(seed, palette) { seededColor(seed, palette) }
}

internal fun seededColor(seed: String, palette: SeedPalette): Color {
    var hash = 0
    for (c in seed) hash = hash * 31 + c.code
    val n = hash and 0x7fffffff

    val slot = n % SLOTS
    // -SPREAD..+SPREAD, evenly spaced, centred on the theme hue.
    val offset = -SPREAD + (2f * SPREAD) * (slot.toFloat() / (SLOTS - 1))
    val hue = (palette.hue + offset).mod(360f)

    // Two lightness steps, alternating independently of the hue slot, so two
    // entities landing on neighbouring hues still separate.
    val lightness = if ((n / SLOTS) % 2 == 0) 0.62f else 0.52f

    return oklch(lightness, palette.chroma, hue)
}
