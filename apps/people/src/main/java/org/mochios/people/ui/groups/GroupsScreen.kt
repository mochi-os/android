// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.EntityListRow
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.NotificationBell
import org.mochios.people.R
import org.mochios.people.model.Group
import org.mochios.people.ui.components.PeopleSidebar
import org.mochios.people.ui.components.PeopleSidebarSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onOpenGroup: (id: String) -> Unit,
    onSwitchSection: (PeopleSidebarSection) -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupsEvent.OpenGroup -> onOpenGroup(event.id)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PeopleSidebar(
                current = PeopleSidebarSection.GROUPS,
                onSelect = { section ->
                    drawerScope.launch { drawerState.close() }
                    if (section != PeopleSidebarSection.GROUPS) onSwitchSection(section)
                },
            )
        },
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_groups_title)) },
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.people_open_sidebar),
                        )
                    }
                },
                actions = {
                    NotificationBell(onClick = onOpenNotifications)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openCreate() }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.people_groups_create),
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading && uiState.groups.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null && uiState.groups.isEmpty() -> {
                    ErrorState(error = uiState.error!!, onRetry = { viewModel.retry() })
                }

                uiState.groups.isEmpty() -> {
                    EmptyGroups()
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(uiState.groups, key = { it.id }) { group ->
                            GroupRow(group = group, onClick = { onOpenGroup(group.id) })
                        }
                    }
                }
            }
        }
    }
    }

    if (uiState.createDialogOpen) {
        GroupCreateDialog(
            isCreating = uiState.isCreating,
            onDismiss = { viewModel.closeCreate() },
            onCreate = { name, description -> viewModel.createGroup(name, description) },
        )
    }
}

@Composable
private fun GroupRow(group: Group, onClick: () -> Unit) {
    EntityListRow(
        name = group.name,
        seed = group.id,
        icon = Icons.Default.Groups,
        subtitle = group.description.takeIf { it.isNotBlank() },
        onClick = onClick,
    )
}

@Composable
private fun EmptyGroups() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.people_groups_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GroupCreateDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val nameInvalid = name.isBlank()

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.people_groups_create)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.people_group_name)) },
                    singleLine = true,
                    isError = name.isNotEmpty() && nameInvalid,
                    supportingText = if (name.isNotEmpty() && nameInvalid) {
                        { Text(stringResource(R.string.people_group_name_required)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.people_group_description_optional)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, description) },
                enabled = !nameInvalid && !isCreating,
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.people_groups_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(stringResource(R.string.people_common_cancel))
            }
        },
    )
}
