package org.mochios.projects.ui.board

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
import androidx.compose.material3.ModalBottomSheet
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
import org.mochios.projects.R
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.ui.project.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveObjectSheet(
    obj: ProjectObject,
    viewModel: ProjectViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val uiState by viewModel.uiState.collectAsState()
    val activeView = viewModel.getActiveView() ?: run { onDismiss(); return }
    val columnFieldId = activeView.columns.takeIf { it.isNotBlank() } ?: run { onDismiss(); return }
    val columnOptions = viewModel.getAllOptionsForField(columnFieldId)
    val currentColumnValue = obj.stringValue(columnFieldId)
    val rowFieldId = activeView.rows.takeIf { it.isNotBlank() }
    val rowOptions = rowFieldId?.let { viewModel.getAllOptionsForField(it) } ?: emptyList()
    val currentRowValue = rowFieldId?.let { obj.stringValue(it) } ?: ""

    // Objects in the same column for reordering
    val columnObjects = uiState.objects.filter { it.stringValue(columnFieldId) == currentColumnValue && it.id != obj.id }
    val prefix = uiState.projectDetails?.project?.prefix ?: ""

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Column selection
            Text(
                text = stringResource(R.string.projects_move_to_column),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(columnOptions, key = { it.id }) { option ->
                    val isSelected = option.id == currentColumnValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isSelected) {
                                    viewModel.moveObject(obj.id, columnFieldId, option.id, 0)
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
                                contentDescription = stringResource(R.string.projects_move_current),
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
                            text = stringResource(R.string.projects_move_to_row),
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
                                        viewModel.moveObject(obj.id, rowFieldId, option.id, null, option.id)
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
                                    contentDescription = stringResource(R.string.projects_move_current),
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
                            text = stringResource(R.string.projects_move_position),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    item(key = "pos_top") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveObject(obj.id, columnFieldId, currentColumnValue, 0)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.projects_move_position_top), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    items(columnObjects, key = { "after_${it.id}" }) { other ->
                        val label = other.readable.ifBlank { "$prefix-${other.number}" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveObject(obj.id, columnFieldId, currentColumnValue, other.rank + 1)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.projects_move_after, label),
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
