// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.model.AccessRule

/**
 * Card rendering for a single access rule, shared across crm, feeds, forums,
 * and projects settings screens.
 *
 * `levelLabel` resolves the rule's `operation` string to a localised label —
 * each app passes its own mapper because the set of meaningful access levels
 * varies (forums has post/moderate; projects has owner/design/write; feeds
 * has react). The card has no opinion on which levels exist.
 *
 * When `onLevelChange` is supplied along with a non-empty `levels` list and the
 * rule is not the owner, the level chip becomes a dropdown that mirrors web's
 * inline level select: tapping it lists `levels` (each labelled via
 * `levelLabel`) and reports the chosen one. Owners always render a static chip.
 * Callers whose level label depends on more than the operation string (e.g.
 * grant-aware deny labels) should leave `onLevelChange` null and keep the
 * static chip.
 */
@Composable
fun AccessRuleCard(
    rule: AccessRule,
    levelLabel: @Composable (operation: String) -> String,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier,
    levels: List<String> = emptyList(),
    onLevelChange: ((operation: String) -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name ?: rule.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rule.isOwner) {
                    Text(
                        text = stringResource(R.string.access_owner),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val chipColors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            if (onLevelChange != null && levels.isNotEmpty() && !rule.isOwner) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { expanded = true },
                        label = { Text(levelLabel(rule.operation)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = chipColors,
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        levels.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(levelLabel(level)) },
                                onClick = {
                                    expanded = false
                                    if (level != rule.operation) onLevelChange(level)
                                },
                            )
                        }
                    }
                }
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text(levelLabel(rule.operation)) },
                    colors = chipColors,
                )
            }
            if (!rule.isOwner) {
                IconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.access_revoke),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
