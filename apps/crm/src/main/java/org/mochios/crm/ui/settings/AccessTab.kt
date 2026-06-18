package org.mochios.crm.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.model.AccessRule
import org.mochios.android.ui.components.AccessRuleCard
import org.mochios.crm.R
import org.mochios.android.R as MochiR

private val ACCESS_LEVEL_KEYS = listOf("owner", "design", "write", "comment", "view", "none")

// Levels offered when changing an existing rule's level inline, mirroring web's
// CRM_ACCESS_LEVELS select (no "owner" — ownership isn't reassigned this way).
private val ACCESS_LEVEL_CHANGE_KEYS = ACCESS_LEVEL_KEYS.filter { it != "owner" }

@Composable
private fun accessLevelLabel(value: String): String = when (value) {
    "owner" -> stringResource(R.string.crm_access_level_owner)
    "design" -> stringResource(R.string.crm_access_level_design)
    "write" -> stringResource(R.string.crm_access_level_write)
    "comment" -> stringResource(R.string.crm_access_level_comment)
    "view" -> stringResource(R.string.crm_access_level_view)
    "none" -> stringResource(R.string.crm_access_level_none)
    else -> value
}

@Composable
fun AccessTab(
    uiState: CrmSettingsUiState,
    viewModel: CrmSettingsViewModel
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
                        onRevoke = { viewModel.revokeAccess(rule.subject) },
                        levels = ACCESS_LEVEL_CHANGE_KEYS,
                        onLevelChange = { level -> viewModel.setAccess(rule.subject, level) },
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
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onAdd = { subject, level ->
                viewModel.setAccess(subject, level)
                showAddDialog = false
            }
        )
    }
}

/**
 * Add-access dialog mirroring web's AccessDialog: pick the subject as a searched
 * user (entity id), a friend group (`@id`), or a manually-typed target, then
 * choose the level. Users are searched live via the crm users/search proxy;
 * groups are fetched once on first switch into the Groups tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    viewModel: CrmSettingsViewModel,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var userQuery by remember { mutableStateOf("") }
    var manualTarget by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("view") }
    var levelExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && uiState.groups.isEmpty()) viewModel.loadGroups()
    }

    val effectiveTarget = if (selectedTab == 2) manualTarget.trim() else selectedSubject

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MochiR.string.access_add_rule_title)) },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; selectedSubject = ""; selectedName = "" },
                        text = { Text(stringResource(R.string.crm_access_tab_users)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; selectedSubject = ""; selectedName = "" },
                        text = { Text(stringResource(R.string.crm_access_tab_groups)) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; selectedSubject = ""; selectedName = "" },
                        text = { Text(stringResource(R.string.crm_access_tab_manual)) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = if (selectedName.isNotEmpty()) selectedName else userQuery,
                            onValueChange = {
                                userQuery = it
                                selectedSubject = ""
                                selectedName = ""
                                viewModel.searchUsers(it)
                            },
                            label = { Text(stringResource(R.string.crm_access_search_users)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (uiState.userSearchResults.isNotEmpty() && selectedSubject.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Column {
                                    uiState.userSearchResults.take(5).forEach { user ->
                                        TextButton(
                                            onClick = { selectedSubject = user.id; selectedName = user.name },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(user.name, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (uiState.groups.isEmpty()) {
                            Text(
                                stringResource(R.string.crm_access_no_groups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Column {
                                    uiState.groups.forEach { group ->
                                        TextButton(
                                            onClick = {
                                                selectedSubject = "@${group.id}"
                                                selectedName = group.name
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(
                                                    selected = selectedSubject == "@${group.id}",
                                                    onClick = null,
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(group.name)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = manualTarget,
                            onValueChange = { manualTarget = it },
                            label = { Text(stringResource(R.string.crm_access_target)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.crm_access_target_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = levelExpanded,
                    onExpandedChange = { levelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = accessLevelLabel(level),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(MochiR.string.access_level)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false }
                    ) {
                        ACCESS_LEVEL_KEYS.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(accessLevelLabel(value)) },
                                onClick = {
                                    level = value
                                    levelExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(effectiveTarget, level) },
                enabled = effectiveTarget.isNotBlank()
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
