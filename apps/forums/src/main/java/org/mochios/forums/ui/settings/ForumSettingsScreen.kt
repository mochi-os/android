// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.NotificationsClearRow
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.forums.R
import org.mochios.forums.model.Forum
import org.mochios.android.R as MochiR

/**
 * Forum settings, styled to match the feeds settings screen: an icon [TabRow]
 * over [Section] cards. Three tabs — General (identity, banner, manage, delete),
 * Access (access rules + members), and AI.
 */
private enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    General(R.string.forums_tab_general, Icons.Outlined.Settings),
    Access(R.string.forums_tab_access, Icons.Outlined.Shield),
    Ai(R.string.forums_tab_ai, Icons.Outlined.AutoAwesome),
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
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onForumDeleted()
    }

    // Surface transient errors (post-load) via the snackbar; a hard load failure
    // with no forum yet still renders as centred text below.
    LaunchedEffect(uiState.error) {
        val err = uiState.error
        if (err != null && uiState.forum.id.isNotEmpty()) {
            snackbarHostState.showSnackbar(err.userMessage())
            viewModel.clearError()
        }
    }

    // Confirm successful edits (rename, banner, access, members, AI) via the
    // snackbar, mirroring feed settings.
    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                    TabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedIndex == index,
                                onClick = { selectedTabKey = tab.name },
                                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                icon = { Icon(tab.icon, contentDescription = null) },
                                text = {
                                    Text(
                                        stringResource(tab.titleRes),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                    when (selectedTab) {
                        SettingsTab.General -> GeneralTab(viewModel, onModeration, onUnsubscribed)
                        SettingsTab.Access -> AccessTab(viewModel)
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
    var bannerDraft by remember(uiState.banner) { mutableStateOf(uiState.banner) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { viewModel.loadBanner() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForumIdentitySection(forum = uiState.forum, onRename = { name -> viewModel.rename(name) })

        // Banner
        Section(
            title = stringResource(R.string.forums_tab_banner),
            description = stringResource(R.string.forums_banner_description),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = bannerDraft,
                    onValueChange = { value -> bannerDraft = value },
                    placeholder = { Text(stringResource(R.string.forums_banner_placeholder)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveBanner(bannerDraft)
                        focusManager.clearFocus()
                    },
                    enabled = bannerDraft != uiState.banner,
                ) {
                    Text(stringResource(MochiR.string.common_save))
                }
            }
        }

        // Manage — notifications, unsubscribe, and (when permitted) moderation.
        Section(title = stringResource(R.string.forums_settings_section_manage)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            }
        }

        // Delete forum
        Section(
            title = stringResource(R.string.forums_settings_delete),
            description = stringResource(R.string.forums_settings_delete_description),
            headerAlignment = Alignment.CenterVertically,
            action = {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(MochiR.string.common_delete))
                }
            },
            content = {},
        )
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

/**
 * Identity card: forum name (inline editable) plus copyable entity id,
 * fingerprint, and server chips. Mirrors feeds' `FeedIdentitySection`.
 */
@Composable
private fun ForumIdentitySection(
    forum: Forum,
    onRename: (String) -> Unit,
) {
    Section(title = stringResource(R.string.forums_settings_section_identity)) {
        IdentityFieldRow(label = stringResource(R.string.forums_settings_field_name)) {
            NameEditor(
                currentName = forum.name,
                onRename = onRename,
                modifier = Modifier.weight(1f),
            )
        }
        IdentityFieldRow(label = stringResource(R.string.forums_settings_field_entity_id)) {
            DataChip(value = forum.id, truncate = Truncate.MIDDLE)
        }
        if (forum.fingerprint.isNotBlank()) {
            IdentityFieldRow(label = stringResource(R.string.forums_settings_field_fingerprint_label)) {
                DataChip(value = forum.fingerprint, truncate = Truncate.MIDDLE)
            }
        }
        if (forum.server.isNotBlank()) {
            IdentityFieldRow(label = stringResource(R.string.forums_settings_field_server)) {
                DataChip(value = forum.server, truncate = Truncate.MIDDLE)
            }
        }
    }
}

/** Identity row with a fixed-width label so values align in a column. */
@Composable
private fun IdentityFieldRow(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        content()
    }
}

/** Inline name display with a pencil that swaps in a text field + confirm/cancel. */
@Composable
private fun NameEditor(
    currentName: String,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember(currentName) { mutableStateOf(currentName) }

    if (isEditing) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = editValue,
                onValueChange = { value -> editValue = value },
                singleLine = true,
                trailingIcon = if (editValue.isNotEmpty()) {
                    {
                        IconButton(onClick = { editValue = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription =
                                    stringResource(R.string.forums_settings_name_clear_cd),
                            )
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                onRename(editValue.trim())
                isEditing = false
            }) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.forums_settings_save_name),
                )
            }
            IconButton(onClick = {
                editValue = currentName
                isEditing = false
            }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.forums_settings_name_cancel_cd),
                )
            }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            // fill = false keeps the pencil next to the name instead of pushed to
            // the far end, while the weight still lets a long name wrap.
            Text(currentName, modifier = Modifier.weight(1f, fill = false))
            IconButton(
                onClick = {
                    editValue = currentName
                    isEditing = true

                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.forums_settings_name_edit_cd),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
