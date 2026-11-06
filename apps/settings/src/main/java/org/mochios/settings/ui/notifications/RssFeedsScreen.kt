// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.notifications.RssFeed
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiFab
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.rememberCopier
import org.mochios.settings.R
import org.mochios.android.R as MochiR

/**
 * The RSS feeds of the user's notifications, as the web's RSS feeds dialog
 * lists them: create one, copy its address into an RSS reader, rename it,
 * delete it, and choose whether it joins each new category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedsScreen(
    onBack: () -> Unit,
    viewModel: RssFeedsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val copier = rememberCopier()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RssFeed?>(null) }
    var deleting by remember { mutableStateOf<RssFeed?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                when (event) {
                    is RssFeedsEvent.Done -> context.getString(event.message)
                    is RssFeedsEvent.Failure -> event.error.userMessage()
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_rss)) },
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
        floatingActionButton = {
            if (!state.isLoading && state.error == null) {
                MochiFab(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.notifications_rss_create))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.feeds.isEmpty() -> ErrorState(
                    error = state.error!!,
                    onRetry = { viewModel.load() },
                )
                state.feeds.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.RssFeed,
                    title = stringResource(R.string.notifications_rss_empty),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.feeds, key = { it.id }) { feed ->
                        RssFeedCard(
                            feed = feed,
                            busy = feed.id in state.busy,
                            onCopy = { copier.copy(viewModel.address(feed)) },
                            onRename = { renaming = feed },
                            onDelete = { deleting = feed },
                            onEnabled = { enabled -> viewModel.setEnabled(feed, enabled) },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        CreateRssFeedDialog(
            busy = "create" in state.busy,
            onDismiss = { creating = false },
            onCreate = { name, existing -> viewModel.create(name, existing) { creating = false } },
        )
    }

    state.created?.let { feed ->
        val address = viewModel.address(feed)
        MochiAlertDialog(
            onDismissRequest = { viewModel.dismissCreated() },
            title = stringResource(R.string.notifications_rss_created),
            confirmText = stringResource(R.string.notifications_rss_done),
            onConfirm = { viewModel.dismissCreated() },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                MochiIconButton(onClick = { copier.copy(address) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(MochiR.string.common_copy))
                }
            }
        }
    }

    renaming?.let { feed ->
        var name by remember(feed.id) { mutableStateOf(feed.name) }
        MochiAlertDialog(
            onDismissRequest = { renaming = null },
            title = stringResource(R.string.notifications_rss_edit),
            confirmText = stringResource(MochiR.string.common_save),
            onConfirm = {
                viewModel.rename(feed, name)
                renaming = null
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        ) {
            MochiTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.notifications_rss_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    deleting?.let { feed ->
        MochiAlertDialog(
            onDismissRequest = { deleting = null },
            title = stringResource(R.string.notifications_rss_delete_title),
            text = stringResource(R.string.notifications_rss_delete_message),
            confirmText = stringResource(MochiR.string.common_delete),
            onConfirm = {
                viewModel.delete(feed)
                deleting = null
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

@Composable
private fun RssFeedCard(
    feed: RssFeed,
    busy: Boolean,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    feed.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MochiIconButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(MochiR.string.common_copy))
                }
                MochiIconButton(onClick = onRename, enabled = !busy) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.notifications_rss_edit))
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    MochiIconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.notifications_rss_delete))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    stringResource(R.string.notifications_rss_default),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Switch(checked = feed.enabled == 1, onCheckedChange = onEnabled)
            }
        }
    }
}

@Composable
private fun CreateRssFeedDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, existing: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var existing by remember { mutableStateOf(true) }
    val fallback = stringResource(R.string.notifications_rss_default_name)
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.notifications_rss_create_title),
        confirmText = stringResource(R.string.notifications_rss_create),
        onConfirm = { onCreate(name.trim().ifEmpty { fallback }, existing) },
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MochiTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.notifications_rss_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.notifications_rss_existing),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = existing, onCheckedChange = { existing = it })
            }
        }
    }
}
