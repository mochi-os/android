// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
                    // Render the rule's effective level (a deny rule, grant=0,
                    // reads as "none") so the chip and the grant-independent
                    // level dropdown share one label function.
                    val effective = rule.copy(
                        operation = if (rule.grant == 0) "none" else rule.operation,
                    )
                    AccessRuleCard(
                        rule = effective,
                        levelLabel = { op -> accessLevelLabel(op) },
                        onRevoke = { viewModel.revokeAccess(rule.subject) },
                        levels = ACCESS_LEVEL_CHANGE_KEYS,
                        onLevelChange = { level -> viewModel.setAccess(rule.subject, level) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddAccessDialog(
            viewModel = viewModel,
            onConfirm = { target, level ->
                viewModel.setAccess(target, level)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

// Grant-independent level label. Callers pass the rule's *effective* level
// ("none" for a deny rule), so this maps each level — including "none" — to its
// label, and the inline level-change dropdown can reuse it per option.
@Composable
private fun accessLevelLabel(operation: String): String = when (operation) {
    "view" -> stringResource(R.string.forums_access_level_view)
    "vote" -> stringResource(R.string.forums_access_level_vote)
    "comment" -> stringResource(R.string.forums_access_level_comment)
    "post" -> stringResource(R.string.forums_access_level_post)
    "moderate" -> stringResource(R.string.forums_access_level_moderate)
    "none" -> stringResource(R.string.forums_access_level_none)
    else -> operation
}

// Levels offered when changing an existing rule inline (web filters out "none"
// — denying is done via revoke). Highest-to-lowest, matching web's order.
private val ACCESS_LEVEL_CHANGE_KEYS = listOf("moderate", "post", "comment", "vote", "view")

/**
 * Add-access dialog mirroring web's `AccessDialog`: pick the subject as a
 * searched user (entity id), a friend group (`@id`), or a manually-typed
 * target (entity id / @group / * / +), then choose the level. Users are
 * searched live via the forums `users/search` proxy; groups are fetched once
 * on first switch into the Groups tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    viewModel: ForumSettingsViewModel,
    onConfirm: (target: String, level: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var userQuery by remember { mutableStateOf("") }
    var manualTarget by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
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

    // Load groups on first switch into the Groups tab.
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && uiState.groups.isEmpty()) {
            viewModel.loadGroups()
        }
    }

    // The effective target depends on the active tab: searched/grouped picks
    // produce selectedSubject; the Manual tab uses the typed value directly.
    val effectiveTarget = if (selectedTab == 2) manualTarget.trim() else selectedSubject

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forums_access_add)) },
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
                        text = { Text(stringResource(R.string.forums_access_tab_users)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            selectedSubject = ""
                            selectedName = ""
                        },
                        text = { Text(stringResource(R.string.forums_access_tab_groups)) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            selectedSubject = ""
                            selectedName = ""
                        },
                        text = { Text(stringResource(R.string.forums_access_tab_manual)) },
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
                            label = { Text(stringResource(R.string.forums_access_search_users)) },
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
                                            onClick = {
                                                selectedSubject = user.id
                                                selectedName = user.name
                                            },
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
                                stringResource(R.string.forums_access_no_groups),
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
                            label = { Text(stringResource(R.string.forums_access_target)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.forums_access_target_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
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
            Button(
                onClick = { onConfirm(effectiveTarget, level) },
                enabled = effectiveTarget.isNotBlank(),
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
