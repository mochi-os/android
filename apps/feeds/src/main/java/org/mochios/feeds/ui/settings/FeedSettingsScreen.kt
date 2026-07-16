// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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

    // Owners/admins get the full tabbed editor; plain subscribers get a
    // read-only identity card plus an unsubscribe action.
    val canManage = permissions.manage

    val tabIds = listOf(SettingsTab.General, SettingsTab.Access, SettingsTab.Ai)

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
                    IconButton(onClick = onNavigateBack) {
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
                    TabRow(
                        selectedTabIndex = selectedIndex,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        // Primary colour is reserved for the selected tab's
                        // divider; the labels stay neutral.
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    ) {
                        tabIds.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedIndex == index,
                                onClick = { selectedTabKey = tab.name },
                                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                icon = { Icon(tab.icon, contentDescription = null) },
                                text = {
                                    Text(
                                        stringResource(tab.titleRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }

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

/**
 * Read-only settings shown to a plain subscriber: the feed's identity card and
 * an unsubscribe action.
 */
@Composable
private fun SubscriberSettings(
    feed: Feed,
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
        FeedIdentitySection(feed = feed, editable = false, onRename = {})

        Section(
            title = stringResource(R.string.feeds_settings_unsubscribe_section),
            action = {
                OutlinedButton(onClick = { showConfirm = true }) {
                    Text(stringResource(R.string.feeds_unsubscribe))
                }
            },
            headerAlignment = Alignment.CenterVertically,
            content = {},
        )
    }

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.feeds_unsubscribe_confirm),
            message = stringResource(R.string.feeds_unsubscribe_confirm_message),
            confirmLabel = stringResource(R.string.feeds_unsubscribe),
            isDestructive = true,
            onConfirm = {
                showConfirm = false
                onUnsubscribe()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

private enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    General(R.string.feeds_settings, Icons.Outlined.Settings),
    Access(R.string.feeds_tab_access, Icons.Outlined.Shield),
    Ai(R.string.feeds_tab_ai, Icons.Outlined.AutoAwesome),
}
