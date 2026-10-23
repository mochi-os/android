// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.sessions

import androidx.compose.foundation.BorderStroke
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
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusTone
import org.mochios.settings.R
import org.mochios.settings.api.OtherToken
import org.mochios.settings.api.Session
import org.mochios.settings.ui.networks.networkLabel
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
        if (failure != null && state.hasContent()) {
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
                    MochiIconButton(onClick = onBack) {
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
                state.error != null && !state.hasContent() -> ErrorState(
                    error = state.error!!,
                    onRetry = viewModel::refresh,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.sessions.isEmpty()) {
                        item("empty") {
                            Text(
                                text = stringResource(R.string.sessions_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // The server marks the caller's own session; the list
                    // order says nothing about which one that is.
                    itemsIndexed(state.sessions) { _, session ->
                        SessionRow(
                            session = session,
                            isCurrent = session.current,
                            revoking = state.revoking == session.id,
                            onRevoke = { onSuccess -> viewModel.revoke(session.id, onSuccess) },
                        )
                    }
                    tokens(R.string.sessions_signins, state.signins, state.revoking, viewModel::revokeToken)
                    tokens(R.string.sessions_passwords, state.passwords, state.revoking, viewModel::revokeToken)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: Session,
    isCurrent: Boolean,
    revoking: Boolean,
    onRevoke: (onSuccess: () -> Unit) -> Unit,
) {
    val format = LocalFormat.current
    var confirm by remember(session.id) { mutableStateOf(false) }
    MochiCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (isCurrent) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.agent.ifBlank { stringResource(R.string.sessions_unknown_agent) },
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            R.string.account_last_used,
                            format.formatRelativeTime(session.accessed),
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(
                            label = stringResource(R.string.sessions_current),
                            tone = StatusTone(
                                background = MaterialTheme.colorScheme.primaryContainer,
                                foreground = MaterialTheme.colorScheme.onPrimaryContainer,
                                darkBackground = MaterialTheme.colorScheme.primaryContainer,
                                darkForeground = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
                if (session.address.isNotBlank()) {
                    Text(
                        session.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MochiIconButton(onClick = { confirm = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.sessions_revoke),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirm) {
        // Stays open, its button spinning, until the revoke answers; only
        // success closes it.
        MochiAlertDialog(
            onDismissRequest = { if (!revoking) confirm = false },
            title = stringResource(R.string.sessions_revoke_title),
            text = stringResource(R.string.sessions_revoke_message),
            confirmText = stringResource(R.string.sessions_revoke),
            onConfirm = { onRevoke { confirm = false } },
            confirmLoading = revoking,
            destructive = true,
            dismissText = stringResource(R.string.account_cancel),
            dismissEnabled = !revoking,
        )
    }
}

private fun SessionsUiState.hasContent() =
    sessions.isNotEmpty() || signins.isNotEmpty() || passwords.isNotEmpty()

/** A section of sign-ins by other networks' apps, or of minted passwords, under its title. */
private fun androidx.compose.foundation.lazy.LazyListScope.tokens(
    title: Int,
    tokens: List<OtherToken>,
    revoking: String?,
    onRevoke: (String, () -> Unit) -> Unit,
) {
    if (tokens.isEmpty()) return
    item("title:$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    itemsIndexed(tokens, key = { _, token -> "token:" + token.hash }) { _, token ->
        TokenRow(
            token = token,
            revoking = revoking == token.hash,
            onRevoke = { onSuccess -> onRevoke(token.hash, onSuccess) },
        )
    }
}

/**
 * A sign-in by another network's app, or a password minted for one: the app
 * or the device, then the account it is for.
 */
@Composable
internal fun TokenRow(
    token: OtherToken,
    revoking: Boolean,
    onRevoke: (onSuccess: () -> Unit) -> Unit,
) {
    val format = LocalFormat.current
    var confirm by remember(token.hash) { mutableStateOf(false) }
    val signin = token.kind == "signin"
    val title = if (signin) token.client.name.ifEmpty { token.name } else token.name.ifEmpty { token.device }
    val never = stringResource(R.string.sessions_never)
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (signin && token.device.isNotEmpty()) {
                    Text(
                        token.device,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    token.address + " · " + networkLabel(token.network),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(
                        R.string.account_created,
                        if (token.created > 0) format.formatRelativeTime(token.created) else never,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.account_last_used,
                        if (token.used > 0) format.formatRelativeTime(token.used) else never,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MochiIconButton(onClick = { confirm = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.sessions_revoke),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirm) {
        MochiAlertDialog(
            onDismissRequest = { if (!revoking) confirm = false },
            title = stringResource(if (signin) R.string.sessions_revoke_signin_title else R.string.sessions_revoke_password_title),
            text = if (signin) {
                stringResource(R.string.sessions_revoke_signin_message, title, token.address)
            } else {
                stringResource(R.string.sessions_revoke_password_message, token.address)
            },
            confirmText = stringResource(R.string.sessions_revoke),
            onConfirm = { onRevoke { confirm = false } },
            confirmLoading = revoking,
            destructive = true,
            dismissText = stringResource(R.string.account_cancel),
            dismissEnabled = !revoking,
        )
    }
}
