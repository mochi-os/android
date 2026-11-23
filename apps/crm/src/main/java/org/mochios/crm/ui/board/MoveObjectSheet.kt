// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiSheetHeader
import org.mochios.android.ui.components.MochiSheetPadding
import org.mochios.crm.R
import org.mochios.crm.model.CrmObject
import org.mochios.crm.ui.crm.CrmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveObjectSheet(
    obj: CrmObject,
    viewModel: CrmViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val uiState by viewModel.uiState.collectAsState()
    val activeView = viewModel.getActiveView() ?: run { onDismiss(); return }
    val columnFieldId = activeView.columns.takeIf { it.isNotBlank() } ?: run { onDismiss(); return }
    // This object's own class's options: the sheet writes the id it picks
    // straight back as the object's value, and another class's option is one
    // the server rejects.
    val columnOptions = viewModel.getOptionsForObject(obj.objectClass, columnFieldId)
    // The column the board draws it in: a value it draws no column for is in
    // the one for no value, which a move among its cards keeps.
    val currentColumnValue = viewModel.boardColumn(obj, columnFieldId)
    val rowFieldId = activeView.rows.takeIf { it.isNotBlank() }
    val rowOptions = rowFieldId?.let { fieldId ->
        viewModel.getOptionsForObject(obj.objectClass, fieldId)
    } ?: emptyList()
    val currentRowValue = rowFieldId?.let { obj.stringValue(it) } ?: ""

    // The cards beside it on the board, in the order the board draws them.
    val columnObjects = viewModel.movePlaces(obj.id)
    val untitled = stringResource(R.string.crm_untitled)

    MochiBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        MochiSheetHeader(
            title = stringResource(R.string.crm_move_to_column),
        )
        Column(modifier = Modifier.padding(MochiSheetPadding)) {
            // Column selection

            LazyColumn {
                items(columnOptions, key = { it.id }) { option ->
                    val isSelected = option.id == currentColumnValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isSelected) {
                                    viewModel.moveToTop(obj.id, columnFieldId, option.id)
                                }
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (option.colour.isNotBlank()) {
                            Surface(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape),
                                color = parseColor(option.colour)
                            ) { }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.crm_move_current),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Row (swimlane) selection
                if (rowFieldId != null && rowOptions.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.crm_move_to_row),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(rowOptions, key = { "row_${it.id}" }) { option ->
                        val isSelected = option.id == currentRowValue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isSelected) {
                                        viewModel.moveObject(obj.id, rowField = rowFieldId, rowValue = option.id)
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.crm_move_current),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Position within column
                if (columnObjects.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.crm_move_position),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    item(key = "pos_top") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveToTop(obj.id, columnFieldId, currentColumnValue)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.crm_move_position_top), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    items(columnObjects, key = { "after_${it.id}" }) { other ->
                        val otherTitleField = uiState.crmDetails?.classes?.find { it.id == other.objectClass }
                            ?.title?.takeIf { it.isNotBlank() }
                        val label = otherTitleField?.let { other.stringValue(it) }.orEmpty().ifBlank { untitled }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveBeside(obj.id, target = other.id, below = true, field = columnFieldId, value = currentColumnValue)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.crm_move_after, label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
