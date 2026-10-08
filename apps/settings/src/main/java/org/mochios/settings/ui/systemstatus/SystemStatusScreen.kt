// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemstatus

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.CopyButton
import org.mochios.android.util.webUri
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.SystemUpdateInfo
import org.mochios.settings.ui.PeerName
import org.mochios.settings.ui.hyphenateFingerprint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.mochios.settings.ui.login.StepUpHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusScreen(
    onBack: () -> Unit,
    viewModel: SystemStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    StepUpHost(viewModel.stepUp)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_status_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    MochiIconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.system_status_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> ErrorState(
                    error = state.error!!,
                    onRetry = viewModel::refresh,
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusRow(
                        label = stringResource(R.string.system_status_version),
                        value = state.serverVersion,
                        valueWeight = FontWeight.Medium,
                    )
                    StatusRow(
                        label = stringResource(R.string.system_status_started),
                        value = formatSystemTimestamp(state.serverStarted),
                        valueMono = true,
                    )
                    if (state.serverFingerprint.isNotBlank()) {
                        StatusRow(
                            label = stringResource(R.string.account_identity_fingerprint),
                            value = hyphenateFingerprint(state.serverFingerprint),
                            valueMono = true,
                        )
                    }
                    if (state.serverPeerId.isNotBlank()) {
                        StatusRow(
                            label = stringResource(R.string.system_status_peer_id),
                            value = state.serverPeerId,
                            valueMono = true,
                        )
                    }
                    val counts = state.counts
                    if (counts != null) {
                        CountRow(
                            label = stringResource(R.string.system_status_users),
                            count = counts.users,
                        )
                        CountRow(
                            label = stringResource(R.string.system_status_entities),
                            count = counts.entities,
                        )
                    }
                    val network = state.network
                    if (network != null) {
                        val reachability = when (network.reachability) {
                            "public" -> stringResource(R.string.system_status_reachability_public)
                            "private" -> stringResource(R.string.system_status_reachability_private)
                            else -> stringResource(R.string.system_status_reachability_unknown)
                        }
                        StatusRow(
                            label = stringResource(R.string.system_status_reachability),
                            value = reachabilityValue(reachability, network.relay),
                        )
                        if (network.last > 0) {
                            StatusRow(
                                label = stringResource(R.string.system_status_broadcast),
                                value = formatSystemTimestamp(network.last),
                                valueMono = true,
                            )
                        }
                        if (network.holepunch.success + network.holepunch.failure > 0) {
                            StatusRow(
                                label = stringResource(R.string.system_status_holepunch),
                                value = holepunchValue(network.holepunch.success, network.holepunch.failure),
                            )
                        }
                        if (network.relaying.active) {
                            StatusRow(
                                label = stringResource(R.string.system_status_relay_service),
                                value = relayingValue(
                                    network.relaying.reservations.held,
                                    network.relaying.reservations.maximum,
                                    network.relaying.circuits,
                                    network.relaying.rejected,
                                ),
                            )
                        }
                        CountRow(
                            label = stringResource(R.string.system_status_awaiting_routing),
                            count = network.unresolved,
                        )
                        CountRow(
                            label = stringResource(R.string.system_status_peer_queued),
                            count = state.peers.sumOf { it.queued },
                        )
                        CountRow(
                            label = stringResource(R.string.system_status_queued_broadcasts),
                            count = network.queued,
                        )
                    }
                    val update = state.update
                    if (update != null && (update.available || update.pending.isNotBlank())) {
                        UpdateBlock(
                            info = update,
                            isInstalling = state.isInstalling,
                            installError = state.installError?.userMessage(),
                            onInstall = { viewModel.installUpdate() },
                            onOpen = { openUrl(context, it) },
                        )
                    }
                    if (state.peers.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.system_status_peers),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = peerTotals(state.peers.size, state.peers.count { it.connected }, state.network?.mesh),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.peers.forEach { peer -> PeerCard(peer) }
                    }
                }
            }
        }
    }
}

/**
 * Hole punches that worked and that failed. Each count is its own phrase, so
 * each noun or participle agrees with its own number.
 */
@Composable
internal fun holepunchValue(success: Int, failure: Int): String =
    listOf(
        pluralStringResource(R.plurals.system_status_holepunch_succeeded, success, number(success)),
        pluralStringResource(R.plurals.system_status_holepunch_failed, failure, number(failure)),
    ).joinToString(" · ")

/** The relay service's load; "3 / 10 reservations" puts the noun beside the maximum. */
@Composable
internal fun relayingValue(held: Int, maximum: Int, circuits: Int, rejected: Int): String =
    listOf(
        pluralStringResource(R.plurals.system_status_relay_reservations, maximum, number(held), number(maximum)),
        pluralStringResource(R.plurals.system_status_relay_circuits, circuits, number(circuits)),
        pluralStringResource(R.plurals.system_status_relay_refused, rejected, number(rejected)),
    ).joinToString(" · ")

/** How the server is reached, noting when that is through a relay. */
@Composable
internal fun reachabilityValue(reachability: String, relay: Boolean): String =
    if (relay) stringResource(R.string.system_status_reachability_relay, reachability) else reachability

/**
 * The peer counts over the list, as one phrase per language. A server that
 * reports no network information has no broadcast mesh to count.
 */
@Composable
internal fun peerTotals(known: Int, connected: Int, mesh: Int?): String =
    if (mesh != null) {
        stringResource(R.string.system_status_peers_totals, number(known), number(connected), number(mesh))
    } else {
        stringResource(R.string.system_status_peers_totals_without_mesh, number(known), number(connected))
    }

/** A count as the user writes numbers, grouped by their chosen separator. */
@Composable
private fun number(value: Int): String = LocalFormat.current.formatNumber(value)

@Composable
private fun PeerCard(peer: org.mochios.settings.api.PeerEntry) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PeerName(peer.name)
            Text(
                text = hyphenateFingerprint(peer.fingerprint).ifBlank { peer.peer },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    when {
                        peer.connected -> R.string.system_status_peer_connected
                        peer.unreachable -> R.string.system_status_peer_unreachable
                        else -> R.string.system_status_peer_disconnected
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (peer.address.isNotBlank()) {
                StatusRow(
                    label = stringResource(R.string.system_status_peer_address),
                    value = peer.address,
                    valueMono = true,
                )
            }
            if (peer.seen > 0) {
                StatusRow(
                    label = stringResource(R.string.system_status_peer_seen),
                    value = formatSystemTimestamp(peer.seen),
                    valueMono = true,
                )
            }
            CountRow(
                label = stringResource(R.string.system_status_peer_queued),
                count = peer.queued,
            )
            StatusRow(
                label = stringResource(R.string.system_status_peer_oldest),
                value = if (peer.queued > 0) formatSystemTimestamp(peer.oldest) else "-",
                valueMono = true,
            )
        }
    }
}

/** A row whose value is a count, written as the user writes numbers. */
@Composable
internal fun CountRow(label: String, count: Int) {
    StatusRow(label = label, value = number(count))
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueMono: Boolean = false,
    valueWeight: FontWeight = FontWeight.Normal,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(192.dp),
        )
        Text(
            text = value,
            style = if (valueMono) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontFamily = if (valueMono) FontFamily.Monospace else null,
            fontWeight = valueWeight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UpdateBlock(
    info: SystemUpdateInfo,
    isInstalling: Boolean,
    installError: String?,
    onInstall: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = stringResource(R.string.system_status_update),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (info.pending.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.system_status_installing, info.pending),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.system_status_update_available, info.latest),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                UpdateAction(
                    info = info,
                    isInstalling = isInstalling,
                    onInstall = onInstall,
                    onOpen = onOpen,
                )
                if (installError != null) {
                    Text(
                        text = installError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateAction(
    info: SystemUpdateInfo,
    isInstalling: Boolean,
    onInstall: () -> Unit,
    onOpen: (String) -> Unit,
) {
    when (info.platform) {
        "linux-deb" -> CommandHint(
            command = "sudo apt update && sudo apt install mochi-server",
        )
        "linux-rpm" -> CommandHint(
            command = "sudo dnf upgrade mochi-server",
        )
        "docker" -> CommandHint(
            command = "docker compose pull && docker compose up -d",
        )
        "windows" -> MochiButton(
            onClick = onInstall,
            enabled = !isInstalling,
        ) {
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.system_status_install_update))
        }
        "macos-arm64" -> DownloadLink(
            label = stringResource(R.string.system_status_download_installer),
            url = "https://packages.mochi-os.org/macos/mochi-server-arm64.pkg",
            onOpen = onOpen,
        )
        "macos-amd64" -> DownloadLink(
            label = stringResource(R.string.system_status_download_installer),
            url = "https://packages.mochi-os.org/macos/mochi-server-amd64.pkg",
            onOpen = onOpen,
        )
        else -> DownloadLink(
            label = stringResource(R.string.system_status_download_from_packages),
            url = "https://packages.mochi-os.org/",
            onOpen = onOpen,
        )
    }
}

@Composable
private fun CommandHint(command: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        CopyButton(
            value = command,
            contentDescription = stringResource(R.string.system_status_copy),
        )
    }
}

@Composable
private fun DownloadLink(label: String, url: String, onOpen: (String) -> Unit) {
    MochiOutlinedButton(onClick = { onOpen(url) }) {
        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

// Always YYYY-MM-DD HH:MM:SS — mirrors web's formatSystemTimestamp. Fixed
// format ignoring user preferences because this is an admin/diagnostic page.
private fun formatSystemTimestamp(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(Date(epochSeconds * 1000))
}

private fun openUrl(context: Context, url: String) {
    val target = webUri(url) ?: return
    val intent = Intent(Intent.ACTION_VIEW, target).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
