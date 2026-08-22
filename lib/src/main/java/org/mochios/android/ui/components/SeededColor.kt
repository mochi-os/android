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
 * The seed picks one of [SLOTS] positions on an arc measured from the theme's
 * own hue, rather than anywhere on the 360° circle. Staying inside the arc is
 * what keeps a row of circles looking like one family instead of a bag of
 * highlighters. Three numbers below decide whether that family is also
 * legible, and all three were wrong on the first pass:
 *
 *  - **Eight slots, not twelve.** Twelve across this arc put them 10° apart,
 *    which is a ΔE near 0.028 in Oklab against a just-noticeable difference
 *    of about 0.02 — so neighbouring slots read as the same colour and two
 *    entities looked alike. Eight puts them ~16° apart, a ΔE near 0.044.
 *    Fewer hues that separate beat more that don't; the lost variety comes
 *    back through the lightness tier, which doubles the set either way.
 *  - **The arc is asymmetric**, [ARC_START] to [ARC_END], leaning away from
 *    yellow. Yellow needs more lightness than red to read as a clean colour,
 *    so holding lightness fixed while swinging that way turns the far slots
 *    to olive and brown. Swinging the other way, into magenta and violet,
 *    costs nothing perceptually and stays inside the palette.
 *  - **Four lightness tiers, not two.** Eight hues by two tiers gave sixteen
 *    colours, and sixteen is too few to go round: a fifteen-row list draws
 *    about ten distinct colours from that set, so five pairs of unrelated
 *    entities match. Doubling through lightness rather than through hue buys
 *    the room without pushing hues back under the JND or reaching for a
 *    muddy one. It halves the expected collisions; it cannot end them, and
 *    no palette that still looks like one family would. A seeded colour is
 *    for recognising the same entity across screens, not for telling two
 *    apart within a list.
 *
 * [TIERS] tops out at 0.62 because of the cool themes, not the warm ones.
 * White on teal is the weakest pairing the system can produce — at hue 190
 * a 0.63 tier measures 3.03:1 against the 3.0 that non-text contrast asks
 * for, and 0.64 fails outright. Pinned here the worst case is 3.15:1 on a
 * cool theme and 3.75:1 on a warm one, so raise this only with the teal
 * measured again.
 */
private const val SLOTS = 8
private const val ARC_START = -75f
private const val ARC_END = 30f
private val TIERS = floatArrayOf(0.62f, 0.56f, 0.50f, 0.44f)

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
    // Evenly spaced along the arc, measured from the theme hue.
    val offset = ARC_START + (ARC_END - ARC_START) * (slot.toFloat() / (SLOTS - 1))
    val hue = (palette.hue + offset).mod(360f)

    // Lightness cycles independently of the hue slot, so two entities landing
    // on neighbouring hues still separate.
    val lightness = TIERS[(n / SLOTS) % TIERS.size]

    return oklch(lightness, palette.chroma, hue)
}
