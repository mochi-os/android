// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.R
import org.mochios.android.account.MochiAccount
import org.mochios.android.ui.auth.AuthNavigation
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton

/**
 * Single entry point for app rendering: the host activity supplies its app name
 * and the content to show once bootstrap reaches [AuthStage.Ready].
 */
@Composable
fun AppBootstrapHost(
    appName: String,
    oauthScheme: String?,
    onLocaleChangeRequested: () -> Unit,
    prefetchApps: List<String> = emptyList(),
    ready: @Composable (onLogout: () -> Unit) -> Unit,
) {
    val viewModel: AppBootstrapViewModel = hiltViewModel()
    val stage by viewModel.stage.collectAsState()

    LaunchedEffect(appName, prefetchApps) {
        viewModel.start(appName, prefetchApps)
    }

    when (val s = stage) {
        is AuthStage.Booting -> Loading()

        is AuthStage.NeedsAccountChoice -> AccountChoice(
            accounts = s.accounts,
            onPick = viewModel::pickAccount,
            onDifferent = viewModel::useDifferentServer
        )

        is AuthStage.NeedsLogin -> AuthNavigation(
            onAuthenticated = viewModel::onAuthSuccess,
            oauthScheme = oauthScheme
        )

        is AuthStage.Bootstrapping -> Loading()

        is AuthStage.NeedsReactivation -> ReactivationScreen(
            purge = s.purge,
            onReactivate = viewModel::reactivate,
            onSignOut = viewModel::logout,
        )

        is AuthStage.Ready -> {
            if (s.recreateForLocale) {
                LaunchedEffect(Unit) { onLocaleChangeRequested() }
                Loading()
            } else {
                // Re-key the ready scope on every fresh Ready so
                // rememberSaveable state, notably the nav back stack, starts
                // clean; otherwise a re-login restores the previous session's
                // routes.
                key(s.epoch) {
                    ready(viewModel::logout)
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Reactivation interstitial for a soft-deleted ("closing") account, mirroring web's
 *  /login/closing. */
@Composable
private fun ReactivationScreen(
    purge: Long,
    onReactivate: () -> Unit,
    onSignOut: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.reactivation_title),
                style = MaterialTheme.typography.titleLarge,
            )
            val body = if (purge > 0) {
                val date = java.text.DateFormat
                    .getDateInstance(java.text.DateFormat.LONG)
                    .format(java.util.Date(purge * 1000))
                stringResource(R.string.reactivation_body_dated, date)
            } else {
                stringResource(R.string.reactivation_body)
            }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            MochiButton(onClick = onReactivate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.reactivation_reactivate))
            }
            MochiOutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reactivation_sign_out))
            }
        }
    }
}

@Composable
private fun AccountChoice(
    accounts: List<MochiAccount.Snapshot>,
    onPick: (MochiAccount.Snapshot) -> Unit,
    onDifferent: () -> Unit
) {
    if (accounts.size == 1) {
        SingleAccountConfirm(accounts.first(), onPick, onDifferent)
    } else {
        AccountPicker(accounts, onPick, onDifferent)
    }
}

@Composable
private fun SingleAccountConfirm(
    account: MochiAccount.Snapshot,
    onContinue: (MochiAccount.Snapshot) -> Unit,
    onDifferent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.bootstrap_welcome_back), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        AccountSummary(account)
        Spacer(modifier = Modifier.height(32.dp))
        MochiButton(
            onClick = { onContinue(account) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                if (account.name.isNotBlank()) {
                    stringResource(R.string.bootstrap_continue_as, account.name)
                } else {
                    stringResource(R.string.auth_continue)
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        MochiTextButton(onClick = onDifferent, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bootstrap_sign_in_differently))
        }
    }
}

@Composable
private fun AccountPicker(
    accounts: List<MochiAccount.Snapshot>,
    onPick: (MochiAccount.Snapshot) -> Unit,
    onDifferent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.bootstrap_choose_account), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        accounts.forEach { account ->
            MochiOutlinedButton(
                onClick = { onPick(account) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(account.name.takeIf { it.isNotBlank() } ?: account.identity)
                    Text(
                        text = account.server,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        MochiTextButton(onClick = onDifferent, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bootstrap_sign_in_different_server))
        }
    }
}

@Composable
private fun AccountSummary(account: MochiAccount.Snapshot) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (account.name.isNotBlank()) {
            Text(text = account.name, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = account.server,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
