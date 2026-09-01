// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/**
 * One row of [ClassListTab] — an object class as the design screens list it.
 *
 * @property id Identifier handed back to `onClassClick`.
 * @property name Label shown on the row.
 * @property rank Sort position within the list.
 */
data class ClassListItem(
    val id: String,
    val name: String,
    val rank: Int
)

/**
 * Wording for [ClassListTab]. Each feature keeps its own translated strings, so
 * the caller resolves them and passes them in rather than the library owning a
 * second copy of every locale.
 *
 * @property empty Placeholder shown when there are no classes.
 * @property addAction Content description of the add button.
 * @property addDialogTitle Title of the create dialog.
 * @property nameLabel Label of the name field in the create dialog.
 * @property createAction Confirm button of the create dialog.
 */
data class ClassListLabels(
    val empty: String,
    val addAction: String,
    val addDialogTitle: String,
    val nameLabel: String,
    val createAction: String
)

/**
 * List of object classes with an add button, shared by the CRM and Projects
 * design screens. Rows are sorted by [ClassListItem.rank]; the button opens a
 * name-only dialog that reports the entered name through [onCreateClass].
 *
 * @param classes Classes to list, in any order.
 * @param labels Feature-specific wording for the empty state, button and dialog.
 * @param onCreateClass Called with the typed name when the dialog is confirmed.
 * @param onClassClick Called with the [ClassListItem.id] of a tapped row.
 * @param modifier Modifier applied to the tab's root container.
 */
@Composable
fun ClassListTab(
    classes: List<ClassListItem>,
    labels: ClassListLabels,
    onCreateClass: (String) -> Unit,
    onClassClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (classes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = labels.empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    classes.sortedBy { cls -> cls.rank },
                    key = { cls -> cls.id }
                ) { cls ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClassClick(cls.id) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = cls.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = labels.addAction)
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        MochiAlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = labels.addDialogTitle,
            content = {
                MochiTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(labels.nameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmText = labels.createAction,
            onConfirm = {
                onCreateClass(name)
                showAddDialog = false
            },
            confirmEnabled = name.isNotBlank(),
            dismissText = stringResource(R.string.common_cancel),
        )
    }
}
