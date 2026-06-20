// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.Truncate
import org.mochios.wikis.R
import org.mochios.wikis.model.Replica

/**
 * Replicas tab body. Lists remote wikis that have replicated this wiki and
 * lets the owner remove a replica subscription. Hidden by the parent screen
 * when the wiki itself is a replica (no nested replicas).
 */
@Composable
fun ReplicasTab(
    parentViewModel: WikiSettingsViewModel,
    viewModel: ReplicasTabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var pendingRemove by remember { mutableStateOf<Replica?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            parentViewModel.emit(msg.messageRes, *msg.args.toTypedArray())
        }
    }

    when {
        state.isLoading && state.replicas.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.replicas.isEmpty() -> {
            EmptyState(
                icon = Icons.Default.Group,
                title = stringResource(R.string.wikis_replicas_empty_title),
                subtitle = stringResource(R.string.wikis_replicas_empty_message),
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.replicas, key = { it.id }) { replica ->
                    ReplicaCard(
                        replica = replica,
                        isRemoving = state.isRemoving,
                        onRemove = { pendingRemove = replica },
                    )
                }
            }
        }
    }

    val toRemove = pendingRemove
    if (toRemove != null) {
        ConfirmDialog(
            title = stringResource(R.string.wikis_replicas_remove_confirm_title),
            message = stringResource(
                R.string.wikis_replicas_remove_confirm_message,
                toRemove.name ?: toRemove.id,
            ),
            confirmLabel = stringResource(R.string.wikis_replicas_remove_confirm_action),
            isDestructive = true,
            onConfirm = {
                viewModel.remove(toRemove.id)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null },
        )
    }
}

@Composable
private fun ReplicaCard(
    replica: Replica,
    isRemoving: Boolean,
    onRemove: () -> Unit,
) {
    val format = LocalFormat.current
    val subscribedLabel = format.formatTimestamp(replica.subscribed)
    val syncedLabel = if (replica.synced > 0) {
        format.formatTimestamp(replica.synced)
    } else {
        stringResource(R.string.wikis_replicas_never_synced)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!replica.name.isNullOrBlank()) {
                    Text(
                        text = replica.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                DataChip(value = replica.id, truncate = Truncate.MIDDLE)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.wikis_replicas_col_subscribed, subscribedLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.wikis_replicas_col_synced, syncedLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemove,
                enabled = !isRemoving,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.wikis_replicas_remove_confirm_action),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
