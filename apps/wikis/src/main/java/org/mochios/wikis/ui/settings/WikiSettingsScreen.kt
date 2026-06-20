// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.mochios.wikis.R
import org.mochios.wikis.navigation.WikisApp
import org.mochios.android.R as MochiR

/**
 * Top-level wiki settings screen. Hosts four tabs — Settings, Redirects,
 * Access, Replicas — and persists the active tab in the URL so back/forward
 * and process death restore the user to the tab they were viewing.
 *
 * Mirrors web's `apps/wikis/web/src/features/wiki/wiki-settings.tsx`. The
 * Replicas tab is hidden when the wiki is a replica itself (has a `source`).
 *
 * Tab content lives in dedicated composables:
 *  - [SettingsTab] — identity, subscription, home page, delete
 *  - [RedirectsTab] — wraps the standalone redirects body
 *  - [AccessTab] — access rules with add/revoke
 *  - [ReplicasTab] — list + remove replica subscriptions
 *
 * The screen owns the single SnackbarHost; tab view models relay messages
 * via [WikiSettingsViewModel.emit].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiSettingsScreen(
    navController: NavController,
    viewModel: WikiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(
                context.getString(msg.messageRes, *msg.args.toTypedArray()),
            )
        }
    }

    val wikiId = viewModel.wikiId
    val isReplica = state.wiki?.source != null

    // Visible tabs. Replicas hidden for replica wikis (web does the same).
    val tabKeys = buildList {
        add(SettingsTabKey.Settings)
        add(SettingsTabKey.Redirects)
        add(SettingsTabKey.Access)
        if (!isReplica) add(SettingsTabKey.Replicas)
    }

    // Read the current tab from the URL via the live back-stack entry. A
    // launchSingleTop navigation to the same route updates the arguments
    // on the existing entry rather than creating a new one, and
    // currentBackStackEntryAsState surfaces those updates as state so the
    // tab strip re-renders. SavedStateHandle on the view model is frozen
    // at construction time, so we deliberately don't read from it here.
    val currentEntry by navController.currentBackStackEntryAsState()
    val activeRoute = currentEntry?.arguments?.getString("tab")
        ?: viewModel.initialTab
    val activeTabKey = tabKeys.firstOrNull { it.routeKey == activeRoute }
        ?: SettingsTabKey.Settings
    val activeIndex = tabKeys.indexOf(activeTabKey).coerceAtLeast(0)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.wiki?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.wikis_settings_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(padding),
        ) {
            if (state.isLoading && state.wiki == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                TabRow(
                    selectedTabIndex = activeIndex,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tabKeys.forEach { tab ->
                        Tab(
                            selected = activeTabKey == tab,
                            onClick = {
                                // Don't push a new entry for the active tab.
                                if (tab != activeTabKey) {
                                    navController.navigate(
                                        WikisApp.settings(wikiId, tab.routeKey),
                                    ) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            text = {
                                Text(
                                    text = stringResource(tab.titleRes),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }

                when (activeTabKey) {
                    SettingsTabKey.Settings -> SettingsTab(
                        navController = navController,
                        parentViewModel = viewModel,
                        onWikiDeleted = {
                            navController.navigate(WikisApp.HOME) {
                                popUpTo(WikisApp.HOME) { inclusive = false }
                            }
                        },
                    )
                    SettingsTabKey.Redirects -> RedirectsTab(parentViewModel = viewModel)
                    SettingsTabKey.Access -> AccessTab(parentViewModel = viewModel)
                    SettingsTabKey.Replicas -> ReplicasTab(parentViewModel = viewModel)
                }
            }
        }
    }
}

/**
 * Stable tab identity. The [routeKey] is the value embedded in the URL
 * (e.g. `?tab=access`), and the [titleRes] is the localised label.
 */
internal enum class SettingsTabKey(val routeKey: String, val titleRes: Int) {
    Settings("settings", R.string.wikis_settings_tab_settings),
    Redirects("redirects", R.string.wikis_settings_tab_redirects),
    Access("access", R.string.wikis_settings_tab_access),
    Replicas("replicas", R.string.wikis_settings_tab_replicas),
}
