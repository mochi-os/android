package org.mochios.wikis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.wikis.R
import org.mochios.android.R as MochiR

/**
 * Access tab body. Lists access rules and lets the user add new ones or
 * revoke existing ones. The Add dialog mirrors the feeds settings AccessTab
 * — users searched live via the wikis backend's `users/search` proxy,
 * groups fetched once on first open. Hierarchical levels (edit > view >
 * none) match the web access-control vocabulary for wikis.
 */
@Composable
fun AccessTab(
    parentViewModel: WikiSettingsViewModel,
    viewModel: AccessTabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // Relay tab snackbars through the host view model so the parent
    // Scaffold's SnackbarHost surfaces them.
    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            parentViewModel.emit(msg.messageRes, *msg.args.toTypedArray())
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.wikis_access_add_action),
                )
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading && state.subjects.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.subjects.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(MochiR.string.access_no_rules),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.subjects, key = { it.subject }) { subject ->
                        AccessSubjectCard(
                            subject = subject,
                            onLevelChange = { level -> viewModel.setAccess(subject.subject, level) },
                            onRevoke = { viewModel.revokeAccess(subject.subject) },
                        )
                    }
                }
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
            },
        )
    }
}

/**
 * A single grouped access row: the subject with its derived level, an inline
 * dropdown to change the level, and a revoke button. Mirrors web's
 * `AccessList` row (one row per subject, derived level, inline level Select).
 * The owner row is read-only — its level isn't editable and it can't be
 * revoked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessSubjectCard(
    subject: AccessSubject,
    onLevelChange: (String) -> Unit,
    onRevoke: () -> Unit,
) {
    val levels = listOf("edit", "view", "none")
    var levelExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = accessSubjectLabel(subject),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subject.isOwner) {
                    Text(
                        text = stringResource(MochiR.string.access_owner),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))

            if (subject.isOwner) {
                // Owner level is fixed (full access); render a static label.
                Text(
                    text = wikiAccessLevelLabel("manage"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = levelExpanded,
                    onExpandedChange = { levelExpanded = it },
                    modifier = Modifier.width(170.dp),
                ) {
                    OutlinedTextField(
                        value = wikiAccessLevelLabel(subject.level),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.wikis_access_change_level)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false },
                    ) {
                        levels.forEach { lvl ->
                            DropdownMenuItem(
                                text = { Text(wikiAccessLevelLabel(lvl)) },
                                onClick = {
                                    levelExpanded = false
                                    if (lvl != subject.level) onLevelChange(lvl)
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(MochiR.string.access_revoke),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Display label for an access subject. Maps the special wildcard subjects to
 * friendly names (mirroring web's `SUBJECT_LABELS`); otherwise prefers the
 * resolved name, falling back to the raw subject id.
 */
@Composable
private fun accessSubjectLabel(subject: AccessSubject): String = when (subject.subject) {
    "*" -> stringResource(R.string.wikis_access_subject_anyone)
    "+" -> stringResource(R.string.wikis_access_subject_authenticated)
    else -> subject.name?.takeIf { it.isNotBlank() } ?: subject.subject
}

/** Localised label for a wiki access level (edit/view/none). */
@Composable
private fun wikiAccessLevelLabel(level: String): String = when (level) {
    "edit" -> stringResource(R.string.wikis_access_level_edit)
    "view" -> stringResource(R.string.wikis_access_level_view)
    "none" -> stringResource(R.string.wikis_access_level_none)
    "manage" -> stringResource(MochiR.string.access_level_manage)
    else -> level.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    viewModel: AccessTabViewModel,
    onDismiss: () -> Unit,
    onAdd: (subject: String, level: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var userQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("edit") }
    var levelExpanded by remember { mutableStateOf(false) }

    // Load groups on first switch into the Groups tab.
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && state.groups.isEmpty()) {
            viewModel.loadGroups()
        }
    }

    val levels = listOf("edit", "view", "none")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MochiR.string.access_add_rule_title)) },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            selectedSubject = ""
                            selectedName = ""
                        },
                        text = { Text(stringResource(R.string.wikis_access_tab_users)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            selectedSubject = ""
                            selectedName = ""
                        },
                        text = { Text(stringResource(R.string.wikis_access_tab_groups)) },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = if (selectedName.isNotEmpty()) selectedName else userQuery,
                        onValueChange = {
                            userQuery = it
                            selectedSubject = ""
                            selectedName = ""
                            viewModel.searchUsers(it)
                        },
                        label = { Text(stringResource(R.string.wikis_access_add_subject_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (state.userSearchResults.isNotEmpty() && selectedSubject.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column {
                                state.userSearchResults.take(5).forEach { user ->
                                    TextButton(
                                        onClick = {
                                            selectedSubject = user.fingerprint ?: user.id
                                            selectedName = user.name
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = user.name,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (state.groups.isEmpty()) {
                        Text(
                            text = stringResource(R.string.wikis_access_no_groups),
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
                                state.groups.forEach { group ->
                                    TextButton(
                                        onClick = {
                                            // Groups are subjects prefixed with @.
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
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(group.name)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = levelExpanded,
                    onExpandedChange = { levelExpanded = it },
                ) {
                    OutlinedTextField(
                        value = wikiAccessLevelLabel(level),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.wikis_access_level_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false },
                    ) {
                        levels.forEach { lvl ->
                            DropdownMenuItem(
                                text = { Text(wikiAccessLevelLabel(lvl)) },
                                onClick = {
                                    level = lvl
                                    levelExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(selectedSubject, level) },
                enabled = selectedSubject.isNotEmpty(),
            ) {
                Text(stringResource(MochiR.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}
