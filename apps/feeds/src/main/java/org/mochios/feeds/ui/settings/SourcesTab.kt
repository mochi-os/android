// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.api.userMessage
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
    val addSourceError by viewModel.addSourceError.collectAsState()

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

    // A permission-required add replaces the add dialog with the permission
    // prompt; close the add dialog so the two don't stack. The retry details
    // are stashed on the pending permission, so allowing it re-runs the add.
    LaunchedEffect(pendingPermission) {
        if (pendingPermission != null) {
            showAddDialog = false
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
            errorMessage = addSourceError?.userMessage(),
            onClearError = { viewModel.clearAddSourceError() },
            onDismiss = {
                showAddDialog = false
                viewModel.clearAddSourceError()
            },
            onAdd = { url, type ->
                viewModel.addSource(url, type) {
                    showAddDialog = false
                }
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

// The credibility pill sweeps a red→green hue ramp — 0 reads red, 100 green —
// matching the web sources list.
private fun credibilityHue(value: Int): Float = (value.coerceIn(0, 100) / 100f) * 130f

/** Small pill showing a credibility score (0–100), tinted by the score. */
@Composable
private fun CredibilityBadge(value: Int) {
    val hue = credibilityHue(value)
    Text(
        text = value.toString(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.hsl(hue, 0.6f, 0.38f),
        modifier = Modifier
            .background(Color.hsl(hue, 0.6f, 0.9f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
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
        "rss" -> Icons.Outlined.RssFeed
        "feed/posts" -> Icons.Outlined.Link
        "feed/memories" -> Icons.Outlined.CalendarMonth
        else -> Icons.Outlined.RssFeed
    }
    val typeLabel = when (source.type) {
        "rss" -> stringResource(R.string.feeds_source_type_rss)
        "feed/posts" -> stringResource(R.string.feeds_source_type_feed_posts)
        "feed/memories" -> stringResource(R.string.feeds_source_type_feed_memories)
        else -> source.type
    }
    // RSS keeps its brand orange; other source types use the theme accent.
    val typeTint = if (source.type == "rss") RssOrange else MaterialTheme.colorScheme.primary
    // Memories sync on demand, so "Never fetched" reads as an error there —
    // hide it until the source has actually been fetched.
    val lastChecked = when {
        source.fetched > 0 -> stringResource(
            R.string.feeds_source_last_checked,
            LocalFormat.current.formatRelativeTime(source.fetched)
        )
        source.type == "feed/memories" -> null
        else -> stringResource(R.string.feeds_source_never_fetched)
    }
    // Human polling cadence, e.g. "Polling every 5 minutes". interval is in
    // seconds; omitted when the source has no schedule.
    val pollingText = source.interval.takeIf { interval -> interval > 0 }?.let { seconds ->
        val duration = if (seconds < 3_600) {
            val minutes = (seconds / 60).coerceAtLeast(1)
            pluralStringResource(R.plurals.feeds_source_interval_minutes, minutes, minutes)
        } else {
            val hours = seconds / 3_600
            pluralStringResource(R.plurals.feeds_source_interval_hours, hours, hours)
        }
        stringResource(R.string.feeds_source_polling_interval, duration)
    }
    // Metadata stacked under the name, one detail per line: the identifier
    // (repeats the URL, shown only when the name isn't already the URL), type,
    // last-checked time, and polling cadence.
    val showIdentifier = source.name.isNotEmpty() && source.url.isNotEmpty()
    val metadataLines = buildList {
        if (showIdentifier) {
            add(source.url)
        }
        add(typeLabel)
        lastChecked?.let { text -> add(text) }
        pollingText?.let { text -> add(text) }
    }
    // Credibility pill beside the name — RSS-only, matching web. Colour tracks
    // the score; hidden when there's no score set.
    val credibilityValue = if (source.type == "rss" && source.credibility > 0) {
        source.credibility.toInt()
    } else {
        null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = typeIcon,
                contentDescription = typeLabel,
                modifier = Modifier.size(22.dp),
                tint = typeTint
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name.ifEmpty { source.url },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (credibilityValue != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CredibilityBadge(value = credibilityValue)
                    }
                }
                metadataLines.forEach { line ->
                    Text(
                        text = line,
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
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.feeds_source_poll), modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(MochiR.string.common_edit), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
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
    errorMessage: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("rss") }
    var typeExpanded by remember { mutableStateOf(false) }

    // On failure the error renders inline under the URL field; drop the keyboard
    // so it's not hidden behind the IME.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            focusManager.clearFocus()
        }
    }

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
                        onValueChange = { newUrl ->
                            url = newUrl
                            // Clear the previous failure once the user edits.
                            if (errorMessage != null) {
                                onClearError()
                            }
                        },
                        label = { Text(stringResource(R.string.feeds_source_url)) },
                        placeholder = { Text(urlHint) },
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { message ->
                            { Text(message) }
                        },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onSave: (String?, Int?, String?) -> Unit
) {
    // Every source can be renamed; non-memories sources also expose an AI
    // transform expression applied to their imported content. RSS sources add a
    // credibility score.
    val isMemories = source.type == "feed/memories"
    val isRss = source.type == "rss"
    var name by remember { mutableStateOf(source.name) }
    val initialCredibility = source.credibility.toInt()
    var credibility by remember { mutableFloatStateOf(initialCredibility.toFloat()) }
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
                if (isRss) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.feeds_source_credibility_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = credibility,
                            onValueChange = { newValue -> credibility = newValue },
                            valueRange = 0f..100f,
                            steps = 99,
                            thumb = {
                                // A small flat circle reads cleaner than the
                                // default elevated pill.
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                )
                            },
                            track = { sliderState ->
                                // Thin track, no tick marks or stop indicator.
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(4.dp),
                                    drawStopIndicator = null,
                                    drawTick = { _, _ -> }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        CredibilityBadge(value = credibility.toInt())
                    }
                }
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
                    val credibilityValue = credibility.toInt()
                    onSave(
                        name.takeIf { it != source.name },
                        credibilityValue.takeIf { value -> isRss && value != initialCredibility },
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
