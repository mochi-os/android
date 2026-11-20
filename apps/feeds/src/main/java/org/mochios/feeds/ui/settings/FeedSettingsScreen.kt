// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.android.ui.components.SubscriberSettings as SubscriberSettingsLayout
import org.mochios.feeds.R
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.PublishState
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSettingsScreen(
    onNavigateBack: () -> Unit,
    onFeedDeleted: () -> Unit,
    onUnsubscribed: () -> Unit,
    onOpenLink: (String) -> Unit = {},
    viewModel: FeedSettingsViewModel = hiltViewModel()
) {
    val feedInfo by viewModel.feedInfo.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val isUnsubscribing by viewModel.isUnsubscribing.collectAsState()
    val aiAccounts by viewModel.aiAccounts.collectAsState()

    // Owners/admins get the full tabbed editor; plain subscribers get a
    // read-only identity card plus an unsubscribe action.
    val canManage = permissions.manage

    // Where a feed publishes is a tab of its own, shown only where there is
    // an account to publish to or a network to set one up on, or the server
    // says why there is none (a private feed's answer), or reading it failed
    // and the tab offers a retry. Asked again when the feed's privacy changes.
    val publishing: PublishingViewModel = hiltViewModel()
    val publishState by publishing.publish.collectAsState()
    val refusal by publishing.refusal.collectAsState()
    val failure by publishing.failure.collectAsState()
    val privacy = feedInfo?.privacy
    LaunchedEffect(privacy, canManage) {
        if (canManage && privacy != null) publishing.loadPublish()
    }
    val offered = publishable(publishState, refusal, failure)
    val tabIds = settingsTabs(
        publishable = offered == true,
        ai = aiAccounts.isNotEmpty(),
    )

    // Persist tab by stable key so it survives back/forward navigation and
    // process death.
    var selectedTabKey by rememberSaveable { mutableStateOf(SettingsTab.General.name) }
    val selectedTab = tabIds.firstOrNull { it.name == selectedTabKey } ?: SettingsTab.General
    val selectedIndex = tabIds.indexOf(selectedTab).coerceAtLeast(0)

    val snackbarHostState = remember { SnackbarHostState() }

    val resources = LocalResources.current
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.userMessage())
            viewModel.clearError()
        }
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(resources.getString(it))
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
            when (settingsBody(isLoading, info, loadError, canManage, offered)) {
                SettingsBody.Failed -> {
                    ErrorState(error = loadError!!, onRetry = viewModel::loadFeedInfo)
                }

                SettingsBody.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                SettingsBody.Subscriber -> {
                    SubscriberSettings(
                        feed = info!!,
                        isUnsubscribing = isUnsubscribing,
                        onUnsubscribe = { viewModel.unsubscribe { onUnsubscribed() } },
                    )
                }

                SettingsBody.Tabs -> {
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
                        SettingsTab.Publish -> PublishTab(publishing, onOpenLink)
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
    isUnsubscribing: Boolean,
    onUnsubscribe: () -> Unit
) {
    SubscriberSettingsLayout(
        unsubscribeTitle = stringResource(R.string.feeds_settings_unsubscribe_section),
        unsubscribeLabel = stringResource(R.string.feeds_unsubscribe),
        confirmTitle = stringResource(R.string.feeds_unsubscribe_confirm),
        confirmMessage = stringResource(R.string.feeds_unsubscribe_confirm_message),
        onUnsubscribe = onUnsubscribe,
        identity = {
            FeedIdentitySection(feed = feed, editable = false, onRename = {})
            // A subscriber's own keyword filters on the feed.
            val publishing: PublishingViewModel = hiltViewModel()
            LaunchedEffect(Unit) { publishing.loadFilters() }
            PublishingEvents(publishing)
            FiltersSection(publishing)
        },
        isBusy = isUnsubscribing,
        keepOpen = true
    )
}

internal enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    General(R.string.feeds_settings, Icons.Outlined.Settings),
    Publish(R.string.feeds_publish_to, Icons.Outlined.Share),
    Access(R.string.feeds_tab_access, Icons.Outlined.Shield),
    Ai(R.string.feeds_tab_ai, Icons.Outlined.AutoAwesome),
}

/**
 * The owner's tabs: Publish to before Access where the feed has somewhere to
 * publish, and AI once the user has an AI-capable account.
 */
internal fun settingsTabs(publishable: Boolean, ai: Boolean): List<SettingsTab> =
    SettingsTab.entries.filter { tab ->
        (tab != SettingsTab.Publish || publishable) && (tab != SettingsTab.Ai || ai)
    }

/** What the settings screen shows. */
internal enum class SettingsBody { Loading, Failed, Subscriber, Tabs }

/**
 * What the settings screen shows: the feed could not be read, and a retry
 * stands in for every setting, a subscriber's own or an owner's; it is on its
 * way, or the owner's tabs wait for whether Publish to is one of them
 * ([offered] null); a subscriber's view; or the owner's tabs.
 */
internal fun settingsBody(
    loading: Boolean,
    feed: Feed?,
    failure: MochiError?,
    manage: Boolean,
    offered: Boolean?,
): SettingsBody = when {
    feed == null && failure != null && !loading -> SettingsBody.Failed
    feed == null || (manage && offered == null) -> SettingsBody.Loading
    !manage -> SettingsBody.Subscriber
    else -> SettingsBody.Tabs
}

/**
 * Whether Publish to is offered: the owner holds an account the feed may
 * publish to, the server offers a network to set one up on, the server
 * refused and the tab says why, or reading it failed and the tab shows the
 * failure with a retry. Null until the server has answered, so the tabs are
 * drawn once, with it or without.
 */
internal fun publishable(state: PublishState?, refusal: MochiError?, failure: MochiError?): Boolean? = when {
    refusal != null || failure != null -> true
    state == null -> null
    else -> state.accounts.isNotEmpty() || state.offered.isNotEmpty()
}

/** Where a public feed publishes, on a tab of its own. */
@Composable
internal fun PublishTab(publishing: PublishingViewModel, onOpenLink: (String) -> Unit) {
    PublishingEvents(publishing)
    val refusal by publishing.refusal.collectAsState()
    val failure by publishing.failure.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val reason = refusal
        val failed = failure
        if (failed != null) {
            InlineErrorState(error = failed, onRetry = publishing::loadPublish)
        } else if (reason != null) {
            // Not a failure to retry: a private feed publishes nowhere.
            Text(
                reason.userMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PublishingSection(publishing, onOpenLink)
        }
    }
}
