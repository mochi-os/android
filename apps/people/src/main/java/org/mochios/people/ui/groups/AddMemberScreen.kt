// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.groups

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.people.R
import org.mochios.people.model.GroupMemberType
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: AddMemberViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedItem by remember {
        mutableStateOf<AddMemberViewModel.SearchResult?>(null)
    }
    val picked = selectedItem

    LaunchedEffect(state.added) {
        if (state.added) onAdded()
    }

    // Back steps out of the confirmation first, so a mistaken tap on a result
    // costs one press rather than the whole search.
    fun goBack() {
        when {
            state.isSaving -> Unit
            picked != null -> selectedItem = null
            else -> onBack()
        }
    }

    BackHandler { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (picked != null) {
                            stringResource(R.string.people_groups_add_member_confirm_title)
                        } else {
                            stringResource(R.string.people_member_add)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }, enabled = !state.isSaving) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (picked != null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        state.error?.let { error ->
                            Text(
                                text = error.userMessage(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { viewModel.addMember(picked) },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(
                                    stringResource(
                                        R.string.people_groups_add_member_confirm_action
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (picked != null) {
                ConfirmRow(picked)
            } else {
                SearchStep(
                    state = state,
                    onSearch = viewModel::search,
                    onSelect = { result -> selectedItem = result },
                )
            }
        }
    }
}

@Composable
private fun SearchStep(
    state: AddMemberViewModel.UiState,
    onSearch: (String) -> Unit,
    onSelect: (AddMemberViewModel.SearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            placeholder = { Text(stringResource(R.string.people_member_search_placeholder)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        when {
            state.searchQuery.isBlank() -> {
                Text(
                    text = stringResource(R.string.people_member_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.searchLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.people_friends_searching))
                }
            }

            state.searchError != null -> {
                Text(
                    text = state.searchError.userMessage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.searchResults.isEmpty() -> {
                Text(
                    text = stringResource(R.string.people_friends_no_people_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        state.searchResults,
                        key = { result -> "${result.type}:${result.id}" },
                    ) { result ->
                        SearchRow(
                            result = result,
                            enabled = !state.isSaving,
                            onClick = { onSelect(result) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(result: AddMemberViewModel.SearchResult) {
    val typePillText = stringResource(
        when (result.type) {
            GroupMemberType.USER -> R.string.people_member_type_user
            GroupMemberType.GROUP -> R.string.people_member_type_group
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (result.type) {
            GroupMemberType.USER -> EntityAvatar(
                name = result.name,
                src = "/people/${result.id}/-/avatar",
                seed = result.id,
                size = 40.dp,
            )
            GroupMemberType.GROUP -> Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = result.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(8.dp))
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(typePillText) },
            leadingIcon = {
                Icon(
                    imageVector = when (result.type) {
                        GroupMemberType.USER -> Icons.Default.Person
                        GroupMemberType.GROUP -> Icons.Default.Group
                    },
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun SearchRow(
    result: AddMemberViewModel.SearchResult,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val typePillText = stringResource(
        when (result.type) {
            GroupMemberType.USER -> R.string.people_member_type_user
            GroupMemberType.GROUP -> R.string.people_member_type_group
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (result.type) {
            GroupMemberType.USER -> EntityAvatar(
                name = result.name,
                src = "/people/${result.id}/-/avatar",
                seed = result.id,
                size = 32.dp,
            )
            GroupMemberType.GROUP -> Box(
                modifier = Modifier
                    .size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = result.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(8.dp))
        AssistChip(
            onClick = onClick,
            enabled = enabled,
            label = { Text(typePillText) },
            leadingIcon = {
                Icon(
                    imageVector = when (result.type) {
                        GroupMemberType.USER -> Icons.Default.Person
                        GroupMemberType.GROUP -> Icons.Default.Group
                    },
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }
}
