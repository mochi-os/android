// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.replication

import android.content.ClipData
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.ReplicationHost
import org.mochios.settings.api.ReplicationLink
import org.mochios.settings.ui.PeerName
import org.mochios.settings.ui.hyphenateFingerprint
import org.mochios.settings.ui.login.StepUpHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplicationScreen(
    onBack: () -> Unit,
    onLeft: () -> Unit,
    viewModel: ReplicationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    val copiedOk = stringResource(R.string.replication_copied)
    val copiedErr = stringResource(R.string.replication_copy_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { ev ->
            when (ev) {
                is ReplicationEvent.Copied -> snackbar.showSnackbar(if (ev.success) copiedOk else copiedErr)
                // The account's copy on this server is gone; sign out.
                is ReplicationEvent.Left -> onLeft()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.replication_title)) },
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
                    item("this-account-header") {
                        SectionHeader(title = stringResource(R.string.replication_this_account_title))
                    }

                    item(key = "this-account-subtitle") {
                        Text(
                            stringResource(R.string.replication_this_account_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    item("this-account-username") {
                        AccountFieldRow(
                            label = stringResource(R.string.replication_username_label),
                            value = state.username,
                            monospace = false,
                            onCopy = {
                                val ok = state.username.isNotBlank()
                                if (ok) {
                                    clipboard.setClip(
                                        ClipData.newPlainText("username", state.username).toClipEntry(),
                                    )
                                }
                                viewModel.reportCopied(ok)
                            },
                        )
                    }
                    item("this-account-peer") {
                        AccountFieldRow(
                            label = stringResource(R.string.replication_server_peer_id_label),
                            value = state.serverPeerId,
                            monospace = true,
                            onCopy = {
                                val ok = state.serverPeerId.isNotBlank()
                                if (ok) {
                                    clipboard.setClip(
                                        ClipData.newPlainText("peer", state.serverPeerId).toClipEntry(),
                                    )
                                }
                                viewModel.reportCopied(ok)
                            },
                        )
                        if (state.serverFingerprint.isNotBlank()) {
                            AccountFieldRow(
                                label = stringResource(R.string.account_identity_fingerprint),
                                value = hyphenateFingerprint(state.serverFingerprint),
                                monospace = true,
                                onCopy = {
                                    val fingerprint = hyphenateFingerprint(state.serverFingerprint)
                                    val ok = fingerprint.isNotBlank()
                                    if (ok) {
                                        clipboard.setClip(
                                            ClipData.newPlainText("fingerprint", fingerprint).toClipEntry(),
                                        )
                                    }
                                    viewModel.reportCopied(ok)
                                },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Removing a replica is operated on the server whose copy
                    // you're deleting: only shown when other hosts exist.
                    if (state.hosts.isNotEmpty()) {
                        item("leave-this-server") {
                            LeaveThisServer(onLeave = { viewModel.leave() })
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (state.links.isNotEmpty()) {
                        item("pending-header") {
                            SectionHeader(
                                title = stringResource(R.string.replication_pending_title),
                                subtitle = stringResource(R.string.replication_pending_subtitle),
                            )
                        }
                        items(state.links, key = { it.peer }) { link ->
                            PendingRow(
                                link = link,
                                onApprove = { viewModel.approve(link.peer) },
                                onDeny = { viewModel.deny(link.peer) },
                            )
                        }
                        item("spacer") { Spacer(Modifier.height(16.dp)) }
                    }

                    item("hosts-header") {
                        SectionHeader(
                            title = stringResource(R.string.replication_hosts_title),
                            subtitle = if (state.hosts.isEmpty()) null
                                else stringResource(R.string.replication_hosts_subtitle),
                        )
                    }
                    if (state.hosts.isEmpty()) {
                        item("hosts-empty") {
                            Text(
                                stringResource(R.string.replication_hosts_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.replication_hosts_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.hosts, key = { it.peer }) { host ->
                            HostRow(host = host, onForget = { viewModel.remove(host.peer) })
                        }
                    }
                }
            }
        }
    }

    StepUpHost(viewModel.stepUp)
}

@Composable
private fun AccountFieldRow(
    label: String,
    value: String,
    monospace: Boolean,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.outlinedCardColors(),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        IconButton(
            onClick = onCopy,
            enabled = value.isNotBlank(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.replication_copy),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

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
private fun PendingRow(
    link: ReplicationLink,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (link.label.isNotBlank()) {
                    Text(link.label, style = MaterialTheme.typography.bodyMedium)
                }
                PeerName(link.name)
                if (link.fingerprint.isNotBlank()) {
                    Text(
                        text = hyphenateFingerprint(link.fingerprint),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = link.peer,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            OutlinedButton(onClick = onApprove) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(stringResource(R.string.replication_approve))
            }
            Spacer(Modifier.height(0.dp))
            OutlinedButton(onClick = onDeny) {
                Icon(Icons.Default.Close, contentDescription = null)
                Text(stringResource(R.string.replication_deny))
            }
        }
    }
}

// A host in the set is informational. To remove a *reachable* replica the user
// signs in on that server and uses "Remove my account from this server". Only an
// *unreachable* host gets an advanced "forget" here (you can't sign in to a down
// server), which removes it and tells it to purge when it reconnects.
@Composable
private fun HostRow(host: ReplicationHost, onForget: () -> Unit) {
    val format = LocalFormat.current
    var confirm by remember(host.peer) { mutableStateOf(false) }
    val unreachable = host.irreparable || offlineActive(host.offline)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (host.name.isNotBlank()) {
                        PeerName(host.name)
                    } else {
                        Text(
                            hyphenateFingerprint(host.fingerprint).ifBlank { host.peer },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (host.irreparable) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(stringResource(R.string.replication_irreparable))
                    } else if (offlineActive(host.offline)) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(stringResource(R.string.replication_offline))
                    }
                }
                if (host.name.isNotBlank() && host.fingerprint.isNotBlank()) {
                    Text(
                        text = hyphenateFingerprint(host.fingerprint),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = host.peer,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (host.added > 0) {
                    Text(
                        text = stringResource(R.string.replication_added, format.formatRelativeTime(host.added)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (unreachable) {
                IconButton(onClick = { confirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.replication_forget),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.replication_forget_title)) },
            text = { Text(stringResource(R.string.replication_forget_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onForget()
                }) { Text(stringResource(R.string.replication_forget_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(MochiR.string.common_cancel)) }
            },
        )
    }
}

// "Remove my account from this server" — the primary, local way to drop a
// replica. Confirm, then the ViewModel runs the step-up gate before leaving.
@Composable
private fun LeaveThisServer(onLeave: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }) {
        Icon(Icons.Default.Delete, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.replication_leave))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.replication_leave_title)) },
            text = { Text(stringResource(R.string.replication_leave_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onLeave()
                }) { Text(stringResource(R.string.replication_leave_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(MochiR.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun StatusBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// Mirror of web's offlineActive: show the badge only once a host has been
// unreachable past the threshold (rides out a restart or blip).
private const val offlineBadgeSeconds = 3600L

private fun offlineActive(since: Long): Boolean {
    if (since <= 0) return false
    return System.currentTimeMillis() / 1000 - since > offlineBadgeSeconds
}
