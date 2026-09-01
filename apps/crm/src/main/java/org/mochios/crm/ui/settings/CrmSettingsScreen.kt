// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.android.ui.components.SubscriberSettings as SubscriberSettingsLayout
import org.mochios.crm.R
import org.mochios.crm.model.Crm
import org.mochios.android.R as MochiR

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
                    MochiIconButton(onClick = onBack) {
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
                crm == null && error != null -> ErrorState(
                    error = error,
                    onRetry = { viewModel.retry() }
                )

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
                    MochiTabRow(
                        tabs = tabs.map { tab ->
                            MochiTab(stringResource(tab.titleRes), tab.icon)
                        },
                        selectedIndex = selectedIndex,
                        onSelect = { index -> selectedTabKey = tabs[index].name },
                    )

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
@Composable
private fun SubscriberSettings(
    crm: Crm,
    onUnsubscribe: () -> Unit
) {
    SubscriberSettingsLayout(
        unsubscribeTitle = stringResource(R.string.crm_settings_unsubscribe_section),
        unsubscribeLabel = stringResource(R.string.crm_settings_unsubscribe),
        confirmTitle = stringResource(R.string.crm_settings_unsubscribe_title),
        confirmMessage = stringResource(R.string.crm_settings_unsubscribe_message),
        onUnsubscribe = onUnsubscribe,
        identity = { CrmIdentitySection(crm = crm) }
    )
}
