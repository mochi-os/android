package org.mochios.forums.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.AccessRuleCard
import org.mochios.forums.R
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessTab(viewModel: ForumSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAccess() }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = { showAdd = true },
            modifier = Modifier.padding(16.dp),
        ) {
            Text(stringResource(R.string.forums_access_add))
        }
        if (uiState.accessRules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.forums_access_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.accessRules, key = { it.subject }) { rule ->
                    AccessRuleCard(
                        rule = rule,
                        levelLabel = { op -> accessLevelLabel(op, rule.grant) },
                        onRevoke = { viewModel.revokeAccess(rule.subject) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddAccessDialog(
            onConfirm = { target, level ->
                viewModel.setAccess(target, level)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun accessLevelLabel(operation: String, grant: Int): String {
    if (grant == 0) return stringResource(R.string.forums_access_level_none)
    return when (operation) {
        "view" -> stringResource(R.string.forums_access_level_view)
        "vote" -> stringResource(R.string.forums_access_level_vote)
        "comment" -> stringResource(R.string.forums_access_level_comment)
        "post" -> stringResource(R.string.forums_access_level_post)
        "moderate" -> stringResource(R.string.forums_access_level_moderate)
        else -> operation
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    onConfirm: (target: String, level: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("view") }
    var levelExpanded by remember { mutableStateOf(false) }
    val levels = listOf(
        "view" to stringResource(R.string.forums_access_level_view),
        "vote" to stringResource(R.string.forums_access_level_vote),
        "comment" to stringResource(R.string.forums_access_level_comment),
        "post" to stringResource(R.string.forums_access_level_post),
        "moderate" to stringResource(R.string.forums_access_level_moderate),
        "none" to stringResource(R.string.forums_access_level_none),
    )
    val levelLabel = levels.firstOrNull { it.first == level }?.second ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forums_access_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.forums_access_target)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.forums_access_target_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = levelExpanded,
                    onExpandedChange = { levelExpanded = it },
                ) {
                    OutlinedTextField(
                        value = levelLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.forums_access_level)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false },
                    ) {
                        levels.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    level = code
                                    levelExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(target.trim(), level) },
                enabled = target.isNotBlank(),
            ) {
                Text(stringResource(MochiR.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}
