// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Titled card section with an optional description and trailing action button,
 * containing one or more [FieldRow] children. Mirrors web's `Section` (used
 * pervasively in wikis settings, user/account, and user/preferences) so the
 * port reads identically across platforms.
 *
 * Usage:
 * ```
 * Section(
 *     title = stringResource(R.string.wikis_about_section),
 *     description = stringResource(R.string.wikis_about_description),
 *     action = { OutlinedButton(onClick = { ... }) { Text("Edit") } },
 * ) {
 *     FieldRow(label = stringResource(R.string.wikis_entity_id)) {
 *         DataChip(value = wikiInfo.id, truncate = Truncate.MIDDLE)
 *     }
 *     FieldRow(label = stringResource(R.string.wikis_fingerprint)) {
 *         DataChip(value = fingerprint, truncate = Truncate.MIDDLE)
 *     }
 * }
 * ```
 *
 * @param title       Section heading, rendered as `titleMedium`.
 * @param description Optional one-line description in `bodySmall` muted style.
 * @param action      Optional trailing composable (typically a small button)
 *                    aligned to the end of the header row.
 * @param headerAlignment Vertical alignment of the title column and the
 *                    [action] within the header row. Defaults to
 *                    [Alignment.Top]; use [Alignment.CenterVertically] to
 *                    centre a lone action button against a single-line title.
 * @param content     The section body — typically a list of [FieldRow]s.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
    headerAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = headerAlignment,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (description != null) {
                        Spacer(modifier = Modifier.padding(top = 4.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (action != null) {
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    action()
                }
            }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content,
            )
        }
    }
}

/**
 * Labelled row inside a [Section]. The label is on the start in muted
 * `bodyMedium`; the value composable is on the end, right-aligned. An optional
 * [description] renders as a second line under the label in a smaller, more
 * muted style. Mirrors web's `FieldRow`.
 *
 * @param label       Field label, e.g. "Entity ID".
 * @param description Optional secondary line under the label.
 * @param value       The value composable — usually a [Text], [DataChip], or
 *                    small inline button.
 */
@Composable
fun FieldRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            value()
        }
    }
}
