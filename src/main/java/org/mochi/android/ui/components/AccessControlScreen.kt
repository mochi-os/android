package org.mochi.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mochi.android.R
import org.mochi.android.model.AccessLevel
import org.mochi.android.model.AccessRule
import org.mochi.android.model.User
import org.mochi.android.model.label

@Composable
fun AccessControlScreen(
    rules: List<AccessRule>,
    onSetAccess: (subject: String, level: String) -> Unit,
    onRevoke: (ruleId: Int) -> Unit,
    onSearchUsers: suspend (query: String) -> List<User>,
    modifier: Modifier = Modifier
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.access_add_rule))
            }
        },
        modifier = modifier
    ) { padding ->
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.access_no_rules),
                    subtitle = stringResource(R.string.access_no_rules_subtitle)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    AccessRuleCard(
                        rule = rule,
                        onRevoke = { onRevoke(rule.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccessDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { subject, level ->
                onSetAccess(subject, level)
                showAddDialog = false
            },
            onSearchUsers = onSearchUsers
        )
    }
}

@Composable
private fun AccessRuleCard(
    rule: AccessRule,
    onRevoke: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name ?: rule.subject,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (rule.isOwner) {
                    Text(
                        text = stringResource(R.string.access_owner),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        AccessLevel.fromValue(rule.operation)?.label() ?: rule.operation
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
            if (!rule.isOwner) {
                IconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.access_revoke),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddAccessDialog(
    onDismiss: () -> Unit,
    onConfirm: (subject: String, level: String) -> Unit,
    onSearchUsers: suspend (query: String) -> List<User>
) {
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var selectedLevel by rememberSaveable { mutableStateOf(AccessLevel.VIEW.value) }
    var showLevelMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.access_add_rule_title)) },
        text = {
            Column {
                if (selectedUser == null) {
                    PersonPicker(
                        onSelect = { user -> selectedUser = user },
                        onSearch = onSearchUsers
                    )
                } else {
                    Text(
                        text = selectedUser!!.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(onClick = { selectedUser = null }) {
                        Text(stringResource(R.string.common_change))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.access_level),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box {
                    OutlinedButton(onClick = { showLevelMenu = true }) {
                        Text(AccessLevel.fromValue(selectedLevel)?.label() ?: selectedLevel)
                    }
                    DropdownMenu(
                        expanded = showLevelMenu,
                        onDismissRequest = { showLevelMenu = false }
                    ) {
                        for (level in AccessLevel.entries) {
                            DropdownMenuItem(
                                text = { Text(level.label()) },
                                onClick = {
                                    selectedLevel = level.value
                                    showLevelMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val user = selectedUser
                    if (user != null) {
                        onConfirm(user.fingerprint ?: user.id.toString(), selectedLevel)
                    }
                },
                enabled = selectedUser != null
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
