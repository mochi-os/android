// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.settings.R
import org.mochios.settings.api.Session
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // An error while content is on screen is shown over it rather than
    // replacing it: the full-screen arm below only fires when there is
    // nothing to show, and nothing on it can reach a refresh to clear it.
    LaunchedEffect(state.error) {
        val failure = state.error
        if (failure != null && state.sessions.isNotEmpty()) {
            snackbar.showSnackbar(failure.userMessage())
            viewModel.clearError()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.sessions.isEmpty() -> ErrorState(
                    error = state.error!!,
                    onRetry = viewModel::refresh,
                )
                state.sessions.isEmpty() -> Text(
                    text = stringResource(R.string.sessions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Sessions are sorted accessed-desc by the ViewModel; the
                    // top entry is the current session (matches web's
                    // heuristic). The `(current)` suffix tells the user which
                    // row revoking will sign them out of.
                    itemsIndexed(state.sessions) { index, session ->
                        SessionRow(
                            session = session,
                            isCurrent = index == 0 && session.accessed > 0,
                            onRevoke = { viewModel.revoke(session.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: Session, isCurrent: Boolean, onRevoke: () -> Unit) {
    val format = LocalFormat.current
    var confirm by remember(session.id) { mutableStateOf(false) }
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.agent.ifBlank { stringResource(R.string.sessions_unknown_agent) },
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.sessions_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    stringResource(R.string.account_last_used, format.formatRelativeTime(session.accessed)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.address.isNotBlank()) {
                    Text(
                        session.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { confirm = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.sessions_revoke),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (confirm) {
        MochiAlertDialog(
            onDismissRequest = { confirm = false },
            title = stringResource(R.string.sessions_revoke_title),
            text = stringResource(R.string.sessions_revoke_message),
            confirmText = stringResource(R.string.sessions_revoke),
            onConfirm = {
                confirm = false
                onRevoke()
            },
            destructive = true,
            dismissText = stringResource(R.string.account_cancel),
        )
    }
}
