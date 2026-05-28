package org.mochios.settings.ui.replication

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
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplicationScreen(
    onBack: () -> Unit,
    viewModel: ReplicationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val copiedOk = stringResource(R.string.replication_copied)
    val copiedErr = stringResource(R.string.replication_copy_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { ev ->
            when (ev) {
                is ReplicationEvent.Copied -> snackbar.showSnackbar(if (ev.success) copiedOk else copiedErr)
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
                                val ok = copyToClipboard(context, "username", state.username)
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
                                val ok = copyToClipboard(context, "peer", state.serverPeerId)
                                viewModel.reportCopied(ok)
                            },
                        )
                        Spacer(Modifier.height(16.dp))
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
                        SectionHeader(title = stringResource(R.string.replication_hosts_title))
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
                            HostRow(host = host, onRemove = { viewModel.remove(host.peer) })
                        }
                    }
                }
            }
        }
    }
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
        IconButton(onClick = onCopy, enabled = value.isNotBlank()) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.replication_copy),
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
            Text(
                text = link.label.ifBlank { link.peer },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
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

@Composable
private fun HostRow(host: ReplicationHost, onRemove: () -> Unit) {
    val format = LocalFormat.current
    var confirm by remember(host.peer) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(host.peer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (host.added > 0) {
                    Text(
                        text = stringResource(R.string.replication_added, format.formatRelativeTime(host.added)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { confirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.replication_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.replication_remove_title)) },
            text = { Text(stringResource(R.string.replication_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onRemove()
                }) { Text(stringResource(R.string.replication_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(MochiR.string.common_cancel)) }
            },
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String): Boolean {
    if (value.isBlank()) return false
    val cb =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    cb.setPrimaryClip(ClipData.newPlainText(label, value))
    return true
}
