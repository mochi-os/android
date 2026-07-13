// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.model.Condition

/**
 * Small pill chip rendering a listing's [Condition]. Mirrors web's
 * "condition pill" overlay on cards: new / used / refurbished, theme
 * driven through `secondaryContainer` so it sits at low visual weight
 * next to the type badge.
 */
@Composable
fun ConditionBadge(
    condition: Condition,
    modifier: Modifier = Modifier,
) {
    val label = when (condition) {
        Condition.NEW -> stringResource(R.string.market_condition_new)
        Condition.USED -> stringResource(R.string.market_condition_used)
        Condition.REFURBISHED -> stringResource(R.string.market_condition_refurbished)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
