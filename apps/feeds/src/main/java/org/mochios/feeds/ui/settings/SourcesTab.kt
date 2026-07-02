// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.feeds.R
import org.mochios.feeds.model.Source
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.R as MochiR

/** Brand orange for the RSS glyph, matching the web sources list. */
private val RssOrange = Color(0xFFEE802F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesTab(
    viewModel: FeedSettingsViewModel,
    scrollToSourceUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val sources by viewModel.sources.collectAsState()
    val isLoading by viewModel.isLoadingSources.collectAsState()
    val suggestedCredibility by viewModel.suggestedCredibility.collectAsState()
    val pendingPermission by viewModel.pendingPermission.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Source?>(null) }
    var showRemoveDialog by remember { mutableStateOf<Source?>(null) }

    val listState = rememberLazyListState()
    // When opened from a post's overflow menu, scroll the list to that
    // post's source. Match on the source URL (the RSS XML feed URL the
    // post carries). Once is enough — don't fight the user if they then
    // scroll away, so guard with a flag that survives source reloads.
    var scrolled by remember(scrollToSourceUrl) { mutableStateOf(false) }
    LaunchedEffect(sources, scrollToSourceUrl) {
        if (scrolled || scrollToSourceUrl.isNullOrEmpty() || sources.isEmpty()) {
            return@LaunchedEffect
        }
        val index = sources.indexOfFirst { it.url == scrollToSourceUrl }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            scrolled = true
        }
    }

    Box(modifier = modifier) {
        when {
            isLoading && sources.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            sources.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.feeds_no_sources),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sources, key = { it.id }) { source ->
                        SourceCard(
                            source = source,
                            onEdit = { showEditDialog = source },
                            onRemove = { showRemoveDialog = source },
                            onPoll = { viewModel.pollSource(source.id) }
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.feeds_add_source))
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            hasMemoriesSource = sources.any { it.type == "feed/memories" },
            onDismiss = { showAddDialog = false },
            onAdd = { url, type ->
                viewModel.addSource(url, type)
                showAddDialog = false
            }
        )
    }

    showEditDialog?.let { source ->
        EditSourceDialog(
            source = source,
            onDismiss = { showEditDialog = null },
            onSave = { name, credibility, transform ->
                viewModel.editSource(source.id, name, credibility, transform)
                showEditDialog = null
            }
        )
    }

    showRemoveDialog?.let { source ->
        RemoveSourceDialog(
            source = source,
            onDismiss = { showRemoveDialog = null },
            onRemove = { deletePosts ->
                viewModel.removeSource(source.id, deletePosts)
                showRemoveDialog = null
            }
        )
    }

    suggestedCredibility?.let { pending ->
        SuggestedCredibilityDialog(
            suggested = pending.suggested,
            onAccept = { viewModel.acceptSuggestedCredibility() },
            onDismiss = { viewModel.dismissSuggestedCredibility() }
        )
    }

    pendingPermission?.let { pending ->
        PermissionRequestDialog(
            appId = pending.app,
            permissionName = pending.name,
            onAllow = { viewModel.allowPendingPermission() },
            onDeny = { viewModel.denyPendingPermission() }
        )
    }
}

/**
 * Permission-request dialog shown when adding a source returns
 * `permission_required`: the requesting app, the resolved permission name, and
 * Deny/Allow actions. Mirrors the web shell's permission prompt.
 */
@Composable
private fun PermissionRequestDialog(
    appId: String,
    permissionName: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.feeds_permission_request_title),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.feeds_permission_request_message, appId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = permissionName,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAllow) {
                Text(stringResource(R.string.feeds_permission_allow))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDeny) {
                Text(stringResource(R.string.feeds_permission_deny))
            }
        }
    )
}

@Composable
private fun SourceCard(
    source: Source,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onPoll: () -> Unit
) {
    // Leading glyph + human label per source type, mirroring the web client's
    // sources list (calendar for memories, link for a Mochi feed, RSS glyph).
    val typeIcon = when (source.type) {
        "rss" -> Icons.Default.RssFeed
        "feed/posts" -> Icons.Default.Link
        "feed/memories" -> Icons.Default.CalendarMonth
        else -> Icons.Default.RssFeed
    }
    val typeLabel = when (source.type) {
        "rss" -> stringResource(R.string.feeds_source_type_rss)
        "feed/posts" -> stringResource(R.string.feeds_source_type_feed_posts)
        "feed/memories" -> stringResource(R.string.feeds_source_type_feed_memories)
        else -> source.type
    }
    // RSS keeps its brand orange; other source types use the theme accent.
    val typeTint = if (source.type == "rss") RssOrange else MaterialTheme.colorScheme.primary
    val lastChecked = if (source.fetched > 0) {
        stringResource(
            R.string.feeds_source_last_checked,
            LocalFormat.current.formatRelativeTime(source.fetched)
        )
    } else {
        stringResource(R.string.feeds_source_never_fetched)
    }
    // Metadata stacked under the name: type, last-checked time, and finally the
    // source identifier — shown only when the name isn't already the URL.
    val showIdentifier = source.name.isNotEmpty() && source.url.isNotEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = typeIcon,
                contentDescription = typeLabel,
                modifier = Modifier.size(22.dp),
                tint = typeTint
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name.ifEmpty { source.url },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = lastChecked,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showIdentifier) {
                    Text(
                        text = source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // RSS and memories sources support an on-demand poll; Mochi feeds
            // sync on their own schedule, so the refresh action is hidden there.
            if (source.type == "rss" || source.type == "feed/memories") {
                IconButton(onClick = onPoll, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.feeds_source_poll), modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(MochiR.string.common_edit), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.feeds_remove),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceDialog(
    hasMemoriesSource: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("rss") }
    var typeExpanded by remember { mutableStateOf(false) }

    // A feed can only have one memories source, so drop that option once one
    // already exists (matching web, which hides it from the add menu).
    val typeOptions = buildList {
        add("rss" to stringResource(R.string.feeds_source_type_rss))
        add("feed/posts" to stringResource(R.string.feeds_source_type_feed_posts))
        if (!hasMemoriesSource) {
            add("feed/memories" to stringResource(R.string.feeds_source_type_feed_memories))
        }
    }
    val typeLabel = typeOptions.firstOrNull { it.first == type }?.second ?: type
    val urlRequired = type != "feed/memories"
    // Placeholder mirrors the web client's per-type add dialogs.
    val urlHint = when (type) {
        "rss" -> stringResource(R.string.feeds_source_url_hint_rss)
        "feed/posts" -> stringResource(R.string.feeds_source_url_hint_feed)
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_add_source)) },
        text = {
            Column {
                if (urlRequired) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.feeds_source_url)) },
                        placeholder = { Text(urlHint) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = typeLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.feeds_source_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typeOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    type = value
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url, type) },
                enabled = !urlRequired || url.isNotBlank()
            ) {
                Text(stringResource(MochiR.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

@Composable
private fun EditSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onSave: (String?, Int?, String?) -> Unit
) {
    // Every source can be renamed; non-memories sources also expose an AI
    // transform expression applied to their imported content.
    val isMemories = source.type == "feed/memories"
    var name by remember { mutableStateOf(source.name) }
    var transform by remember { mutableStateOf(source.transform) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_edit_source)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feeds_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (!isMemories) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = transform,
                        onValueChange = { transform = it },
                        label = { Text(stringResource(R.string.feeds_source_transform)) },
                        placeholder = { Text(stringResource(R.string.feeds_source_transform_hint)) },
                        supportingText = { Text(stringResource(R.string.feeds_source_transform_help)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.takeIf { it != source.name },
                        null,
                        transform.takeIf { it != source.transform }
                    )
                }
            ) {
                Text(stringResource(MochiR.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

@Composable
private fun SuggestedCredibilityDialog(
    suggested: Int,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_suggested_credibility_title)) },
        text = { Text(stringResource(R.string.feeds_suggested_credibility_body, suggested)) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.feeds_suggested_credibility_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feeds_suggested_credibility_keep))
            }
        }
    )
}

@Composable
private fun RemoveSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onRemove: (Boolean) -> Unit
) {
    var deletePosts by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_remove_source)) },
        text = {
            Column {
                Text(stringResource(R.string.feeds_remove_source_confirm, source.name.ifEmpty { source.url }))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = deletePosts,
                        onCheckedChange = { deletePosts = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.feeds_remove_also_delete_posts))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRemove(deletePosts) }) {
                Text(stringResource(R.string.feeds_remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}
