// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.settings.R
import org.mochios.settings.ui.networks.networkLabel
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemFederationScreen(
    onBack: () -> Unit,
    viewModel: SystemFederationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val format = LocalFormat.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.federation_title)) },
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
            val hosts = state.hosts
            val fediverse = networkLabel("fediverse")
            val matrix = networkLabel("matrix")
            val xmpp = networkLabel("xmpp")
            when {
                state.isLoading && hosts == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                hosts == null -> ErrorState(error = state.error!!, onRetry = { viewModel.refresh() })
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    heading("fediverse", fediverse)
                    if (hosts.fediverse.isEmpty()) empty("fediverse")
                    items(hosts.fediverse, key = { "fediverse:" + it.host }) { host ->
                        ServerCard(
                            name = host.host,
                            lines = listOfNotNull(
                                if (host.limited) stringResource(R.string.federation_limited) else null,
                                stringResource(R.string.federation_failures, format.formatNumber(host.failures)) + " · " +
                                    stringResource(R.string.federation_waiting, format.formatNumber(host.waiting)),
                                stringResource(R.string.federation_success, moment(host.success)),
                                stringResource(R.string.federation_failure, moment(host.failure)),
                                stringResource(R.string.federation_next, moment(host.next)),
                                host.error.ifEmpty { null },
                            ),
                        )
                    }
                    heading("matrix", matrix)
                    if (hosts.matrix.isEmpty()) empty("matrix")
                    items(hosts.matrix, key = { "matrix:" + it.server }) { server ->
                        ServerCard(
                            name = server.server,
                            lines = listOf(
                                stringResource(R.string.federation_failures, format.formatNumber(server.failures)) + " · " +
                                    stringResource(R.string.federation_waiting, format.formatNumber(server.waiting)),
                                stringResource(R.string.federation_next, moment(server.next)),
                            ),
                        )
                    }
                    item("xmpp") {
                        Column {
                            Text(xmpp, style = MaterialTheme.typography.titleMedium)
                            val sessions = hosts.xmpp.sessions
                            Text(
                                pluralStringResource(R.plurals.federation_clients, sessions.clients.toInt(), format.formatNumber(sessions.clients)) +
                                    " · " + pluralStringResource(R.plurals.federation_accounts, sessions.accounts.toInt(), format.formatNumber(sessions.accounts)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hosts.xmpp.links.isEmpty()) empty("xmpp")
                    items(hosts.xmpp.links, key = { "xmpp:" + it.domain + " " + it.server }) { link ->
                        ServerCard(
                            name = link.server,
                            lines = listOf(
                                stringResource(R.string.federation_from, link.domain) + " · " +
                                    stringResource(if (link.up) R.string.federation_connected else R.string.federation_not_connected),
                                stringResource(R.string.federation_failures, format.formatNumber(link.failures)) + " · " +
                                    stringResource(R.string.federation_waiting, format.formatNumber(link.waiting)),
                                stringResource(R.string.federation_next, moment(link.next)),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** A time, or a dash for never. */
@Composable
private fun moment(unix: Long): String = if (unix > 0) LocalFormat.current.formatTimestamp(unix) else "-"

private fun LazyListScope.heading(key: String, title: String) {
    item("heading:$key") {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun LazyListScope.empty(key: String) {
    item("empty:$key") {
        Text(
            stringResource(R.string.federation_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServerCard(name: String, lines: List<String>) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            for (line in lines) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
