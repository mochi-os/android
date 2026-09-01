// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.android.ui.components.SubscriberSettings as SubscriberSettingsLayout
import org.mochios.feeds.R
import org.mochios.feeds.model.Feed
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSettingsScreen(
    onNavigateBack: () -> Unit,
    onFeedDeleted: () -> Unit,
    onUnsubscribed: () -> Unit,
    viewModel: FeedSettingsViewModel = hiltViewModel()
) {
    val feedInfo by viewModel.feedInfo.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val aiAccounts by viewModel.aiAccounts.collectAsState()

    // Owners/admins get the full tabbed editor; plain subscribers get a
    // read-only identity card plus an unsubscribe action.
    val canManage = permissions.manage

    // The AI tab only appears once the account has an AI-capable account.
    val tabIds = listOf(SettingsTab.General, SettingsTab.Access, SettingsTab.Ai).filter { tab ->
        tab != SettingsTab.Ai || aiAccounts.isNotEmpty()
    }

    // Persist tab by stable key so it survives back/forward navigation and
    // process death.
    var selectedTabKey by rememberSaveable { mutableStateOf(SettingsTab.General.name) }
    val selectedTab = tabIds.firstOrNull { it.name == selectedTabKey } ?: SettingsTab.General
    val selectedIndex = tabIds.indexOf(selectedTab).coerceAtLeast(0)

    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.userMessage())
            viewModel.clearError()
        }
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(context.getString(it))
            viewModel.clearActionMessage()
        }
    }

    // Load access rules when the Access tab is shown (owners only).
    LaunchedEffect(selectedTab, canManage) {
        if (canManage && selectedTab == SettingsTab.Access) {
            viewModel.loadAccessRules()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val name = feedInfo?.name
                    Text(
                        if (name.isNullOrBlank()) {
                            stringResource(R.string.feeds_settings)
                        } else {
                            stringResource(R.string.feeds_settings_title, name)
                        }
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val info = feedInfo
            when {
                isLoading && info == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                !canManage && info != null -> {
                    SubscriberSettings(
                        feed = info,
                        onUnsubscribe = { viewModel.unsubscribe { onUnsubscribed() } },
                    )
                }

                else -> {
                    MochiTabRow(
                        tabs = tabIds.map { tab ->
                            MochiTab(stringResource(tab.titleRes), tab.icon)
                        },
                        selectedIndex = selectedIndex,
                        onSelect = { index -> selectedTabKey = tabIds[index].name },
                    )

                    when (selectedTab) {
                        SettingsTab.General -> GeneralTab(
                            viewModel = viewModel,
                            onFeedDeleted = onFeedDeleted
                        )
                        SettingsTab.Access -> AccessTab(viewModel = viewModel)
                        SettingsTab.Ai -> AiTab(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
@Composable
private fun SubscriberSettings(
    feed: Feed,
    onUnsubscribe: () -> Unit
) {
    SubscriberSettingsLayout(
        unsubscribeTitle = stringResource(R.string.feeds_settings_unsubscribe_section),
        unsubscribeLabel = stringResource(R.string.feeds_unsubscribe),
        confirmTitle = stringResource(R.string.feeds_unsubscribe_confirm),
        confirmMessage = stringResource(R.string.feeds_unsubscribe_confirm_message),
        onUnsubscribe = onUnsubscribe,
        identity = { FeedIdentitySection(feed = feed, editable = false, onRename = {}) }
    )
}

private enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    General(R.string.feeds_settings, Icons.Outlined.Settings),
    Access(R.string.feeds_tab_access, Icons.Outlined.Shield),
    Ai(R.string.feeds_tab_ai, Icons.Outlined.AutoAwesome),
}
