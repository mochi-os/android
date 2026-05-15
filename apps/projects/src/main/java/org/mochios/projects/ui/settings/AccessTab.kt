package org.mochios.projects.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.mochios.android.model.AccessRule
import org.mochios.android.ui.components.AccessRuleCard
import org.mochios.projects.R
import org.mochios.android.R as MochiR

private val ACCESS_LEVEL_KEYS = listOf("owner", "design", "write", "comment", "view", "none")

@Composable
private fun accessLevelLabel(value: String): String = when (value) {
    "owner" -> stringResource(R.string.projects_access_level_owner)
    "design" -> stringResource(R.string.projects_access_level_design)
    "write" -> stringResource(R.string.projects_access_level_write)
    "comment" -> stringResource(R.string.projects_access_level_comment)
    "view" -> stringResource(R.string.projects_access_level_view)
    "none" -> stringResource(R.string.projects_access_level_none)
    else -> value
}

@Composable
fun AccessTab(
    uiState: ProjectSettingsUiState,
    viewModel: ProjectSettingsViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.accessRules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(MochiR.string.access_no_rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.accessRules, key = { it.id }) { rule ->
                    AccessRuleCard(
                        rule = rule,
                        levelLabel = { op -> accessLevelLabel(op) },
                        onRevoke = { viewModel.revokeAccess(rule.id) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(MochiR.string.access_add_rule))
            }
        }
    }

    if (showAddDialog) {
        AddAccessDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { subject, operation ->
                viewModel.setAccess(subject, operation)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var operation by remember { mutableStateOf("view") }
    var operationExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MochiR.string.access_add_rule_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text(stringResource(R.string.projects_access_user_fingerprint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = operationExpanded,
                    onExpandedChange = { operationExpanded = it }
                ) {
                    OutlinedTextField(
                        value = accessLevelLabel(operation),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(MochiR.string.access_level)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = operationExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = operationExpanded,
                        onDismissRequest = { operationExpanded = false }
                    ) {
                        ACCESS_LEVEL_KEYS.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(accessLevelLabel(value)) },
                                onClick = {
                                    operation = value
                                    operationExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(subject, operation) },
                enabled = subject.isNotBlank()
            ) {
                Text(stringResource(MochiR.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}
