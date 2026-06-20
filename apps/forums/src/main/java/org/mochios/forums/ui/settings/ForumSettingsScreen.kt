// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.NotificationsClearRow
import org.mochios.forums.R
import org.mochios.android.R as MochiR

private enum class SettingsTab(val titleRes: Int) {
    General(R.string.forums_tab_general),
    Access(R.string.forums_tab_access),
    Members(R.string.forums_tab_members),
    Banner(R.string.forums_tab_banner),
    Ai(R.string.forums_tab_ai),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumSettingsScreen(
    onBack: () -> Unit,
    onForumDeleted: () -> Unit,
    onModeration: () -> Unit = {},
    onUnsubscribed: () -> Unit = onForumDeleted,
    viewModel: ForumSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabKey by rememberSaveable { mutableStateOf(SettingsTab.General.name) }
    val tabs = SettingsTab.entries
    val selectedTab = tabs.firstOrNull { it.name == selectedTabKey } ?: SettingsTab.General
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onForumDeleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forums_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.forum.id.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                uiState.error != null && uiState.forum.id.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(uiState.error!!.userMessage(), color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 16.dp,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedIndex == index,
                                onClick = { selectedTabKey = tab.name },
                                text = { Text(stringResource(tab.titleRes), style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                    when (selectedTab) {
                        SettingsTab.General -> GeneralTab(viewModel, onModeration, onUnsubscribed)
                        SettingsTab.Access -> AccessTab(viewModel)
                        SettingsTab.Members -> MembersTab(viewModel)
                        SettingsTab.Banner -> BannerTab(viewModel)
                        SettingsTab.Ai -> AiTab(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralTab(
    viewModel: ForumSettingsViewModel,
    onModeration: () -> Unit,
    onUnsubscribed: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var nameDraft by remember(uiState.forum.id) { mutableStateOf(uiState.forum.name) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.forum.name) {
        if (nameDraft.isEmpty()) nameDraft = uiState.forum.name
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.forums_settings_rename),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = nameDraft,
            onValueChange = { nameDraft = it },
            label = { Text(stringResource(R.string.forums_settings_rename_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { viewModel.rename(nameDraft) },
            enabled = nameDraft.isNotBlank() && nameDraft != uiState.forum.name && !uiState.isSaving,
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.forums_settings_save))
            }
        }

        if (uiState.forum.fingerprint.isNotBlank()) {
            Text(
                stringResource(R.string.forums_settings_fingerprint, uiState.forum.fingerprint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NotificationsClearRow(onClick = { viewModel.clearNotifications() })

        OutlinedButton(
            onClick = { viewModel.unsubscribe(onUnsubscribed) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.forums_settings_unsubscribe))
        }

        if (uiState.forum.canModerate) {
            OutlinedButton(
                onClick = onModeration,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.forums_moderation_title))
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.forums_settings_danger_zone),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = { showDeleteDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.forums_settings_delete))
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.forums_settings_delete_title),
            message = stringResource(R.string.forums_settings_delete_message),
            confirmLabel = stringResource(R.string.forums_settings_delete),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
