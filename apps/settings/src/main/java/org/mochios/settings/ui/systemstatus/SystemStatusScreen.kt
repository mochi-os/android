package org.mochios.settings.ui.systemstatus

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.SystemUpdateInfo
import org.mochios.settings.ui.PeerName
import org.mochios.settings.ui.hyphenateFingerprint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusScreen(
    onBack: () -> Unit,
    viewModel: SystemStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_status_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
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
                state.error != null -> Text(
                    text = state.error!!.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
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
                        StatusRow(
                            label = stringResource(R.string.system_status_users),
                            value = counts.users.toString(),
                        )
                        StatusRow(
                            label = stringResource(R.string.system_status_entities),
                            value = counts.entities.toString(),
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
                            value = if (network.relay) {
                                "$reachability · ${stringResource(R.string.system_status_relay)}"
                            } else {
                                reachability
                            },
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
                                value = stringResource(
                                    R.string.system_status_holepunch_value,
                                    network.holepunch.success,
                                    network.holepunch.failure,
                                ),
                            )
                        }
                        StatusRow(
                            label = stringResource(R.string.system_status_awaiting_routing),
                            value = network.unresolved.toString(),
                        )
                        StatusRow(
                            label = stringResource(R.string.system_status_peer_queued),
                            value = state.peers.sumOf { it.queued }.toString(),
                        )
                        StatusRow(
                            label = stringResource(R.string.system_status_queued_broadcasts),
                            value = network.queued.toString(),
                        )
                    }
                    val update = state.update
                    if (update != null && (update.available || update.pending.isNotBlank())) {
                        UpdateBlock(
                            info = update,
                            isInstalling = state.isInstalling,
                            installError = state.installError?.userMessage(),
                            onInstall = { viewModel.installUpdate() },
                            onCopy = { copyToClipboard(context, "command", it) },
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
                        val knownLabel = stringResource(R.string.system_status_known)
                        val connectedLabel = stringResource(R.string.system_status_peer_connected)
                        val meshLabel = stringResource(R.string.system_status_mesh)
                        val totals = mutableListOf(
                            "$knownLabel ${state.peers.size}",
                            "$connectedLabel ${state.peers.count { it.connected }}",
                        )
                        state.network?.let { totals.add("$meshLabel ${it.mesh}") }
                        Text(
                            text = totals.joinToString(" · "),
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

@Composable
private fun PeerCard(peer: org.mochios.settings.api.PeerEntry) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PeerName(peer.name, peer.verified)
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
            StatusRow(
                label = stringResource(R.string.system_status_peer_queued),
                value = peer.queued.toString(),
            )
            StatusRow(
                label = stringResource(R.string.system_status_peer_oldest),
                value = if (peer.queued > 0) formatSystemTimestamp(peer.oldest) else "-",
                valueMono = true,
            )
        }
    }
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
    onCopy: (String) -> Unit,
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
                    onCopy = onCopy,
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
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    when (info.platform) {
        "linux-deb" -> CommandHint(
            command = "sudo apt update && sudo apt install mochi-server",
            onCopy = onCopy,
        )
        "linux-rpm" -> CommandHint(
            command = "sudo dnf upgrade mochi-server",
            onCopy = onCopy,
        )
        "docker" -> CommandHint(
            command = "docker compose pull && docker compose up -d",
            onCopy = onCopy,
        )
        "windows" -> Button(
            onClick = onInstall,
            enabled = !isInstalling,
            colors = ButtonDefaults.buttonColors(),
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
private fun CommandHint(command: String, onCopy: (String) -> Unit) {
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
        IconButton(onClick = { onCopy(command) }) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.system_status_copy),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DownloadLink(label: String, url: String, onOpen: (String) -> Unit) {
    OutlinedButton(onClick = { onOpen(url) }) {
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

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cb.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
