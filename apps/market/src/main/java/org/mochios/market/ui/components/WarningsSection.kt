// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.market.R
import org.mochios.market.model.Warning as WarningModel

/**
 * Prominent amber card listing staff-issued warnings attached to a
 * listing. Hidden when the list is empty.
 *
 * @param reasonLabel Maps raw warning reasons (e.g. `prohibited`,
 *                    `misleading`) to localised labels at the call site.
 *                    Defaults to the raw reason so unknown codes still
 *                    surface.
 */
@Composable
fun WarningsSection(
    warnings: List<WarningModel>,
    modifier: Modifier = Modifier,
    reasonLabel: (String) -> String = { it },
) {
    if (warnings.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(
                    text = stringResource(R.string.market_warnings_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                warnings.forEach { w ->
                    Column {
                        Text(
                            text = reasonLabel(w.reason),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (w.created > 0L) {
                            Text(
                                text = LocalFormat.current.formatTimestamp(w.created),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
