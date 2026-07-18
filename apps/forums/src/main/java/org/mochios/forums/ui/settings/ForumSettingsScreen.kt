// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.Section
import org.mochios.forums.R
import org.mochios.forums.model.Forum
import org.mochios.android.R as MochiR

/**
 * Forum settings, styled to match the feeds settings screen: an icon [TabRow]
 * over [Section] cards. Every tab edits the forum, so the whole tabbed editor is
 * for viewers who can manage; everyone else gets the read-only identity card.
 * The AI tab is added only when the account has an AI-capable account.
 */
private enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    General(R.string.forums_tab_general, Icons.Outlined.Settings),
    Moderation(R.string.forums_tab_moderation, Icons.Outlined.Gavel),
    Access(R.string.forums_tab_access, Icons.Outlined.Shield),
    Ai(R.string.forums_tab_ai, Icons.Outlined.AutoAwesome),
}

/**
 * Read-only settings shown to a viewer who cannot manage the forum: the forum's
 * identity card and an unsubscribe action. Mirrors feeds' `SubscriberSettings`.
 */
@Composable
private fun SubscriberSettings(
    forum: Forum,
    onUnsubscribe: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForumIdentitySection(forum = forum, editable = false, onRename = {})

        Section(
            title = stringResource(R.string.forums_settings_unsubscribe_section),
            action = {
                OutlinedButton(onClick = { showConfirm = true }) {
                    Text(stringResource(R.string.forums_settings_unsubscribe))
                }
            },
            headerAlignment = Alignment.CenterVertically,
            content = {},
        )
    }

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.forums_list_unsubscribe_title),
            message = stringResource(R.string.forums_list_unsubscribe_message),
            confirmLabel = stringResource(R.string.forums_settings_unsubscribe),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                showConfirm = false
                onUnsubscribe()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumSettingsScreen(
    onBack: () -> Unit,
    onForumDeleted: () -> Unit,
    onUnsubscribed: () -> Unit,
    viewModel: ForumSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabKey by rememberSaveable { mutableStateOf(SettingsTab.General.name) }
    // Every tab edits the forum, so the tabbed editor is manage-only.
    val canManage = uiState.permissions.manage
    // The AI tab only appears once the account has at least one AI-capable account.
    val tabs = SettingsTab.entries.filter { tab ->
        tab != SettingsTab.Ai || uiState.aiAccounts.isNotEmpty()
    }
    val selectedTab = tabs.firstOrNull { it.name == selectedTabKey } ?: SettingsTab.General
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onForumDeleted()
    }

    LaunchedEffect(uiState.unsubscribed) {
        if (uiState.unsubscribed) onUnsubscribed()
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

    // Read the name in the outer scope so this composable re-runs (and rebuilds
    // the top-bar title) when the forum loads, rather than relying on a snapshot
    // read buried inside the title lambda.
    val forumName = uiState.forum.name
    val settingsTitle = if (forumName.isBlank()) {
        stringResource(R.string.forums_settings)
    } else {
        stringResource(R.string.forums_settings_title, forumName)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(settingsTitle) },
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

                !canManage -> SubscriberSettings(
                    forum = uiState.forum,
                    onUnsubscribe = { viewModel.unsubscribe() },
                )

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
                        SettingsTab.General -> GeneralTab(viewModel)
                        SettingsTab.Access -> AccessTab(viewModel)
                        SettingsTab.Moderation -> ModerationTab(
                            onMessage = { messageRes -> viewModel.setActionMessage(messageRes) },
                        )
                        SettingsTab.Ai -> AiTab(viewModel)
                    }
                }
            }
        }
    }
}
