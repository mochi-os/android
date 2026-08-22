// Copyright © 2026 Mochisoft OÜ
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
import androidx.compose.material3.Icon
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
 * Card for one access rule, shared by the crm, feeds, forums and projects
 * settings screens; each app supplies its own `levelLabel`. Passing
 * `onLevelChange` with a non-empty `levels` turns the chip into a dropdown -
 * leave it null when the label depends on more than the operation string.
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
    MochiCard(modifier = modifier.fillMaxWidth()) {
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
                    MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        levels.forEach { level ->
                            MochiDropdownMenuItem(
                                text = { Text(levelLabel(level)) },
                                onClick = {
                                    expanded = false
                                    if (level != rule.operation) onLevelChange(level)
                                },
                                selected = rule.operation == level,
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
                MochiIconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.access_revoke),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
