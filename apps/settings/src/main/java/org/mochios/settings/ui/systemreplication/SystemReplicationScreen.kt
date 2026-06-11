package org.mochios.settings.ui.systemreplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.BootstrapEntry
import org.mochios.settings.api.PendingJoin
import org.mochios.settings.ui.login.StepUpHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemReplicationScreen(
    onBack: () -> Unit,
    viewModel: SystemReplicationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val joinApprovedOk = stringResource(R.string.system_replication_join_approved)
    val joinApprovedErr = stringResource(R.string.system_replication_approval_failed)
    val joinDeniedOk = stringResource(R.string.system_replication_join_denied)
    val joinDeniedErr = stringResource(R.string.system_replication_deny_failed)
    val pairRemovedOk = stringResource(R.string.system_replication_pair_removed)
    val pairRemovedErr = stringResource(R.string.system_replication_pair_remove_failed)
    val peerCopiedOk = stringResource(R.string.system_replication_peer_id_copied)
    val peerCopiedErr = stringResource(R.string.system_replication_copy_failed)
    val addressCopiedOk = stringResource(R.string.system_replication_address_copied)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { ev ->
            val msg = when (ev) {
                is SystemReplicationEvent.JoinApproved -> if (ev.success) joinApprovedOk else joinApprovedErr
                is SystemReplicationEvent.JoinDenied -> if (ev.success) joinDeniedOk else joinDeniedErr
                is SystemReplicationEvent.PairRemoved -> if (ev.success) pairRemovedOk else pairRemovedErr
                is SystemReplicationEvent.PeerCopied -> if (ev.success) peerCopiedOk else peerCopiedErr
                is SystemReplicationEvent.AddressCopied -> if (ev.success) addressCopiedOk else peerCopiedErr
            }
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_replication_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error!!.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("this-server-header") {
                        SectionHeader(title = stringResource(R.string.system_replication_this_server))
                    }
                    item("this-server-row") {
                        ThisServerRow(
                            peer = state.peer,
                            onCopy = {
                                val ok = copyToClipboard(context, "peer", state.peer)
                                viewModel.reportPeerCopied(ok)
                            },
                        )
                    }
                    if (state.addresses.isNotEmpty()) {
                        item("addresses-header") {
                            Spacer(Modifier.height(8.dp))
                            SectionHeader(title = stringResource(R.string.system_replication_addresses))
                        }
                        items(state.addresses, key = { "address-$it" }) { address ->
                            AddressRow(
                                address = address,
                                onCopy = {
                                    val ok = copyToClipboard(context, "address", address)
                                    viewModel.reportAddressCopied(ok)
                                },
                            )
                        }
                    }
                    item("this-server-spacer") { Spacer(Modifier.height(16.dp)) }

                    if (state.joins.isNotEmpty()) {
                        item("joins-header") {
                            SectionHeader(
                                title = stringResource(R.string.system_replication_pending_title),
                                subtitle = stringResource(R.string.system_replication_pending_subtitle),
                            )
                        }
                        items(state.joins, key = { "join-${it.peer}" }) { join ->
                            PendingJoinRow(
                                join = join,
                                onApprove = { viewModel.approveJoin(join.peer) },
                                onDeny = { viewModel.denyJoin(join.peer) },
                            )
                        }
                        item("joins-spacer") { Spacer(Modifier.height(16.dp)) }
                    }

                    item("pair-header") {
                        SectionHeader(title = stringResource(R.string.system_replication_pair_title))
                    }
                    if (state.pair.isEmpty()) {
                        item("pair-empty") {
                            Text(
                                stringResource(R.string.system_replication_pair_empty_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.system_replication_pair_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.pair, key = { "pair-$it" }) { peer ->
                            PairMemberRow(
                                peer = peer,
                                status = pairMemberSyncStatus(peer, state.bootstrap),
                                onRemove = { viewModel.removePair(peer) },
                            )
                        }
                    }
                }
            }
        }
    }

    StepUpHost(viewModel.stepUp)
}

private fun pairMemberSyncStatus(peer: String, bootstrap: List<BootstrapEntry>): SyncStatus {
    val rows = bootstrap.filter { it.peer == peer }
    if (rows.isEmpty()) return SyncStatus.Synced
    return if (rows.all { it.state == "done" }) SyncStatus.Synced else SyncStatus.Syncing
}

private enum class SyncStatus { Synced, Syncing }

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThisServerRow(peer: String, onCopy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = peer,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.system_replication_copy_peer_id),
                )
            }
        }
    }
}

@Composable
private fun AddressRow(address: String, onCopy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.system_replication_copy_address),
                )
            }
        }
    }
}

@Composable
private fun PendingJoinRow(
    join: PendingJoin,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = join.label.ifBlank { join.peer },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onApprove) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.system_replication_approve))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDeny) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.system_replication_deny))
            }
        }
    }
}

@Composable
private fun PairMemberRow(
    peer: String,
    status: SyncStatus,
    onRemove: () -> Unit,
) {
    var confirm by remember(peer) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    peer,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Text(
                    text = when (status) {
                        SyncStatus.Synced -> stringResource(R.string.system_replication_synced)
                        SyncStatus.Syncing -> stringResource(R.string.system_replication_syncing)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { confirm = true }) {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = stringResource(R.string.system_replication_remove_pair),
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.system_replication_remove_title)) },
            text = { Text(stringResource(R.string.system_replication_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onRemove()
                }) { Text(stringResource(R.string.system_replication_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(MochiR.string.common_cancel)) }
            },
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String): Boolean {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    cb.setPrimaryClip(ClipData.newPlainText(label, value))
    return true
}
