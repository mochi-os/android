package org.mochios.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.R
import org.mochios.android.account.MochiAccount
import org.mochios.android.ui.auth.AuthNavigation

/**
 * Single entry point for app rendering. The host activity wires this once
 * with its app name + a callback to navigate into its main UI when the
 * bootstrap reaches [AuthStage.Ready].
 *
 *   AppBootstrapHost(appName = "feeds", oauthScheme = "mochi-feeds") {
 *       FeedsNavigation(...)
 *   }
 *
 * No "isAuthenticated" branching, no "tokenFetched" flag — the stage drives
 * everything.
 */
@Composable
fun AppBootstrapHost(
    appName: String,
    oauthScheme: String?,
    onLocaleChangeRequested: () -> Unit,
    ready: @Composable (onLogout: () -> Unit) -> Unit
) {
    val viewModel: AppBootstrapViewModel = hiltViewModel()
    val stage by viewModel.stage.collectAsState()

    LaunchedEffect(appName) {
        viewModel.start(appName)
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

        is AuthStage.Ready -> {
            if (s.recreateForLocale) {
                LaunchedEffect(Unit) { onLocaleChangeRequested() }
                Loading()
            } else {
                ready(viewModel::logout)
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
        Button(
            onClick = { onContinue(account) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (account.name.isNotBlank()) {
                    stringResource(R.string.bootstrap_continue_as, account.name)
                } else {
                    stringResource(R.string.auth_continue)
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDifferent, modifier = Modifier.fillMaxWidth()) {
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
            OutlinedButton(
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
        TextButton(onClick = onDifferent, modifier = Modifier.fillMaxWidth()) {
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
