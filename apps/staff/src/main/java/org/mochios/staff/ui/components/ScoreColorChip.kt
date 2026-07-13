// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pill-shaped score chip used by the listings and moderation tables.
 *
 * Mirrors the web `scoreColor` helper in
 * `apps/staff/web/src/features/listings/listings-page.tsx`:
 *
 *   - `< 30`  -> green  (low score = safe)
 *   - `< 70`  -> yellow (review threshold)
 *   - `>= 70` -> red    (held for review / high-risk)
 *
 * The Comptroller's scoring is monotone (low = safe, high = risky), so a
 * higher number is *worse* and surfaces in the warmer half of the palette.
 *
 * Tones lean on `MaterialTheme.colorScheme` containers where possible so the
 * chip follows the active Mochi theme. The yellow band has no first-class
 * Material container, so it falls back to a hand-tuned amber that reads on
 * both light and dark surfaces.
 */
@Composable
fun ScoreColorChip(score: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        score < 30 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        score < 70 -> Color(0xFFFFE082) to Color(0xFF6B4F00)
        else -> scheme.errorContainer to scheme.onErrorContainer
    }
    Text(
        text = score.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
