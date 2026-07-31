// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.settings

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
import org.mochios.crm.R
import org.mochios.crm.model.Crm
import org.mochios.android.R as MochiR

/**
 * CRM settings tabs, styled to match the projects settings screen: an icon
 * [TabRow] with the label below each glyph. Both tabs edit the CRM, so the whole
 * tabbed editor is manage-only.
 */
private enum class SettingsTab(val titleRes: Int, val icon: ImageVector) {
    General(R.string.crm_settings_tab_general, Icons.Outlined.Settings),
    Access(R.string.crm_settings_tab_access, Icons.Outlined.Shield),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmSettingsScreen(
    onBack: () -> Unit,
    onCrmDeleted: () -> Unit,
    viewModel: CrmSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabKey by rememberSaveable { mutableStateOf(SettingsTab.General.name) }
    val tabs = SettingsTab.entries
    val selectedTab = tabs.firstOrNull { tab -> tab.name == selectedTabKey } ?: SettingsTab.General
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    // The whole tabbed editor edits the CRM, so it is owner/manage-only;
    // everyone else gets the read-only identity view with an unsubscribe action.
    val canManage = uiState.crm?.owner == 1
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Confirm successful edits (field save, access change, revoke) via the
    // snackbar, mirroring the projects settings screen.
    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
            viewModel.clearActionMessage()
        }
    }

    // Surface transient errors (post-load) via the snackbar; a hard load failure
    // with no CRM yet still renders as centred text below.
    LaunchedEffect(uiState.error) {
        val err = uiState.error
        if (err != null && uiState.crm != null) {
            snackbarHostState.showSnackbar(err.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crm_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val crm = uiState.crm
            val error = uiState.error
            when {
                crm == null && error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(error.userMessage(), color = MaterialTheme.colorScheme.error)
                }

                crm == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                !canManage -> SubscriberSettings(
                    crm = crm,
                    onUnsubscribe = { viewModel.unsubscribe { onCrmDeleted() } }
                )

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
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }

                    when (selectedTab) {
                        SettingsTab.General -> GeneralTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            onCrmDeleted = onCrmDeleted
                        )
                        SettingsTab.Access -> AccessTab(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

/**
 * Read-only settings shown to a viewer who cannot manage the CRM: the CRM's
 * identity card and an unsubscribe action. Mirrors the projects, forum, and feed
 * subscriber views.
 */
@Composable
private fun SubscriberSettings(
    crm: Crm,
    onUnsubscribe: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CrmIdentitySection(crm = crm)

        Section(
            title = stringResource(R.string.crm_settings_unsubscribe_section),
            action = {
                OutlinedButton(onClick = { showConfirm = true }) {
                    Text(stringResource(R.string.crm_settings_unsubscribe))
                }
            },
            headerAlignment = Alignment.CenterVertically,
            content = {}
        )
    }

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.crm_settings_unsubscribe_title),
            message = stringResource(R.string.crm_settings_unsubscribe_message),
            confirmLabel = stringResource(R.string.crm_settings_unsubscribe),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                showConfirm = false
                onUnsubscribe()
            },
            onDismiss = { showConfirm = false }
        )
    }
}
