// Copyright © 2026 Mochisoft OÜ
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
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material.icons.outlined.CloudSync
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
import androidx.navigation.NavController
import org.mochios.android.R as MochiR
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.wikis.R
import org.mochios.wikis.navigation.WikisApp

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

    val isReplica = state.wiki?.source != null

    // Visible tabs. Replicas hidden for replica wikis (web does the same).
    val tabKeys = buildList {
        add(SettingsTabKey.Settings)
        add(SettingsTabKey.Redirects)
        add(SettingsTabKey.Access)
        if (!isReplica) add(SettingsTabKey.Replicas)
    }

    // The tab is screen state, not a destination of its own. Navigating to the
    // route for each tab tore this screen down and built it again - the blink
    // between tabs - and took every tab's loaded state with it. The route's
    // `tab` argument still picks the one that opens.
    var activeRoute by rememberSaveable { mutableStateOf(viewModel.initialTab) }
    val activeTabKey = tabKeys.firstOrNull { tab -> tab.routeKey == activeRoute }
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
                    MochiIconButton(onClick = { navController.popBackStack() }) {
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
            val error = state.error
            if (state.wiki == null && error != null) {
                ErrorState(error = error, onRetry = viewModel::loadInfo)
            } else if (state.wiki == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                MochiTabRow(
                    tabs = tabKeys.map { tab ->
                        MochiTab(stringResource(tab.titleRes), tab.icon)
                    },
                    selectedIndex = activeIndex,
                    onSelect = { index -> activeRoute = tabKeys[index].routeKey },
                )

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
 * (e.g. `?tab=access`), the [titleRes] is the localised label, and [icon] is
 * what the tab is recognised by before the label is read.
 */
internal enum class SettingsTabKey(
    val routeKey: String,
    val titleRes: Int,
    val icon: ImageVector,
) {
    Settings("settings", R.string.wikis_settings_tab_settings, Icons.Outlined.Settings),
    Redirects(
        "redirects",
        R.string.wikis_settings_tab_redirects,
        Icons.Outlined.SubdirectoryArrowRight,
    ),
    Access("access", R.string.wikis_settings_tab_access, Icons.Outlined.Shield),
    Replicas("replicas", R.string.wikis_settings_tab_replicas, Icons.Outlined.CloudSync),
}
