// Copyright © 2026 Mochisoft OÜ
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
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiFab
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.mochiDialogCardColors
import org.mochios.feeds.R
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.model.Source
import org.mochios.feeds.ui.component.hostedFediverse
import org.mochios.android.util.NaturalCompare
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
    val isAddingSource by viewModel.isAddingSource.collectAsState()
    val isSavingSource by viewModel.isSavingSource.collectAsState()
    val publishAccounts by viewModel.publishAccounts.collectAsState()
    val networkAccounts by viewModel.networkAccounts.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadSourceAccounts() }

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
        MochiFab(
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
            busy = isAddingSource,
            accounts = publishAccounts,
            hosted = hostedFediverse(networkAccounts),
            bluesky = networkAccounts.filter { it.type == "bluesky" },
            onClearError = { viewModel.clearAddSourceError() },
            onDismiss = {
                showAddDialog = false
                viewModel.clearAddSourceError()
            },
            onAdd = { url, type, account ->
                viewModel.addSource(url, type, account) {
                    showAddDialog = false
                }
            }
        )
    }

    showEditDialog?.let { source ->
        EditSourceDialog(
            source = source,
            busy = isSavingSource,
            onDismiss = { showEditDialog = null },
            onSave = { name, credibility, transform ->
                viewModel.editSource(source.id, name, credibility, transform) {
                    showEditDialog = null
                }
            }
        )
    }

    showRemoveDialog?.let { source ->
        RemoveSourceDialog(
            source = source,
            busy = isSavingSource,
            onDismiss = { showRemoveDialog = null },
            onRemove = { deletePosts ->
                viewModel.removeSource(source.id, deletePosts) {
                    showRemoveDialog = null
                }
            }
        )
    }

    suggestedCredibility?.let { pending ->
        SuggestedCredibilityDialog(
            suggested = pending.suggested,
            busy = isSavingSource,
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

@Composable
internal fun PermissionRequestDialog(
    appId: String,
    permissionName: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    MochiAlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = stringResource(R.string.feeds_permission_request_title),
        titleAlign = TextAlign.Center,
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.feeds_permission_request_message, appId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                MochiCard(modifier = Modifier.fillMaxWidth(), colors = mochiDialogCardColors()) {
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
        confirmText = stringResource(R.string.feeds_permission_allow),
        onConfirm = onAllow,
        dismissText = stringResource(R.string.feeds_permission_deny),
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
internal fun SourceCard(
    source: Source,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onPoll: () -> Unit
) {
    // Leading glyph + human label per source type, mirroring the web client's
    // sources list (calendar for memories, link for a Mochi feed, RSS glyph).
    val typeIcon = when {
        source.type == "rss" -> Icons.Outlined.RssFeed
        source.type == "feed/memories" -> Icons.Outlined.CalendarMonth
        source.type.startsWith("account/") -> Icons.Outlined.AlternateEmail
        source.type == "person/posts" -> Icons.Outlined.Person
        source.type == "tag/posts" -> Icons.Outlined.Tag
        source.type == "generator/posts" || source.type == "list/posts" -> Icons.AutoMirrored.Outlined.ViewList
        else -> Icons.Outlined.Link
    }
    val typeLabel = sourceTypeLabel(source)
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
            pluralStringResource(R.plurals.feeds_source_interval_minutes, minutes, LocalFormat.current.formatNumber(minutes))
        } else {
            val hours = seconds / 3_600
            pluralStringResource(R.plurals.feeds_source_interval_hours, hours, LocalFormat.current.formatNumber(hours))
        }
        stringResource(R.string.feeds_source_polling_interval, duration)
    }
    // Metadata stacked under the name, one detail per line: the identifier
    // (repeats the URL, shown only when the name isn't already the URL), type,
    // last-checked time, and polling cadence.
    // An account's or a person's source names them already; its url is an id.
    val showIdentifier = source.name.isNotEmpty() && source.url.isNotEmpty() &&
        !source.type.startsWith("account/") && source.type != "person/posts"
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
    MochiCard(
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
                    // How a follow through a hosted account stands.
                    if (source.state == "pending" || source.state == "refused") {
                        Spacer(modifier = Modifier.width(8.dp))
                        FollowBadge(refused = source.state == "refused")
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
            // Sources read on a timer support an on-demand poll; Mochi feeds
            // and follows through an account arrive on their own.
            if (pollable(source)) {
                MochiIconButton(onClick = onPoll, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.feeds_source_poll), modifier = Modifier.size(20.dp))
                }
            }
            MochiIconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(MochiR.string.common_edit), modifier = Modifier.size(20.dp))
            }
            MochiIconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.feeds_remove),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Whether a source is read on a timer, so may be read now. */
internal fun pollable(source: Source): Boolean =
    source.type == "rss" || source.type == "tag/posts" || source.type == "feed/memories" ||
        (source.type == "person/posts" && source.account.isEmpty())

/** A source's kind in the owner's words. */
@Composable
internal fun sourceTypeLabel(source: Source): String = when (source.type) {
    "rss" -> stringResource(R.string.feeds_source_type_rss)
    "feed/posts" -> stringResource(R.string.feeds_source_type_feed_posts)
    "feed/memories" -> stringResource(R.string.feeds_source_type_feed_memories)
    "account/timeline" -> stringResource(R.string.feeds_source_type_account_timeline)
    "account/posts" -> stringResource(R.string.feeds_source_type_account_posts)
    "person/posts" -> stringResource(
        if (source.account.isNotEmpty()) R.string.feeds_source_type_fediverse_posts else R.string.feeds_source_type_bluesky_posts,
    )
    "tag/posts" -> stringResource(R.string.feeds_source_type_hashtag)
    "generator/posts", "list/posts" -> stringResource(R.string.feeds_source_type_bluesky)
    else -> stringResource(R.string.feeds_source_type_feed_posts)
}

@Composable
private fun FollowBadge(refused: Boolean) {
    val colour = if (refused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(if (refused) R.string.feeds_source_follow_refused else R.string.feeds_source_follow_pending),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = colour,
        modifier = Modifier
            .background(colour.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** The kinds of source whose address names an account of the owner's. */
private val ACCOUNT_TYPES = listOf("account/timeline", "account/posts")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceDialog(
    hasMemoriesSource: Boolean,
    errorMessage: String?,
    busy: Boolean,
    // The feed's publish accounts, an account's timeline or posts is read from;
    // the hosted fediverse accounts a person is followed through; the Bluesky
    // accounts a custom feed or list is read through.
    accounts: List<PublishAccount>,
    hosted: List<PublishAccount>,
    bluesky: List<PublishAccount>,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: (url: String, type: String, account: String?) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("rss") }
    var typeExpanded by remember { mutableStateOf(false) }
    var through by remember { mutableStateOf("") }

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
        if (accounts.isNotEmpty()) {
            add("account/timeline" to stringResource(R.string.feeds_source_type_account_timeline))
            add("account/posts" to stringResource(R.string.feeds_source_type_account_posts))
        }
        add("person" to stringResource(R.string.feeds_source_type_person))
        add("tag" to stringResource(R.string.feeds_source_type_hashtag))
        add("bluesky" to stringResource(R.string.feeds_source_type_bluesky))
        if (!hasMemoriesSource) {
            add("feed/memories" to stringResource(R.string.feeds_source_type_feed_memories))
        }
    }
    val typeLabel = typeOptions.firstOrNull { it.first == type }?.second ?: type
    val chooseAccount = type in ACCOUNT_TYPES
    val urlRequired = type != "feed/memories" && !chooseAccount
    // The account a person is followed through or a Bluesky feed read through,
    // offered where there is more than one to choose.
    val throughAccounts = when (type) {
        "person" -> hosted
        "bluesky" -> bluesky
        else -> emptyList()
    }.takeIf { it.size > 1 }.orEmpty()
    // Placeholder mirrors the web client's per-type add dialogs.
    val urlHint = when (type) {
        "rss" -> stringResource(R.string.feeds_source_url_hint_rss)
        "feed/posts" -> stringResource(R.string.feeds_source_url_hint_feed)
        "person" -> stringResource(R.string.feeds_source_url_hint_person)
        "tag" -> stringResource(R.string.feeds_source_url_hint_tag)
        "bluesky" -> stringResource(R.string.feeds_source_url_hint_bluesky)
        else -> ""
    }

    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.feeds_add_source),
        content = {
            Column {
                if (urlRequired) {
                    MochiTextField(
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
                    MochiTextField(
                        value = typeLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.feeds_source_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typeOptions.forEach { (value, label) ->
                            MochiDropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (value != type) {
                                        url = ""
                                        through = ""
                                    }
                                    type = value
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }

                if (chooseAccount) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AccountPicker(
                        label = stringResource(R.string.feeds_source_account),
                        accounts = accounts,
                        chosen = url,
                        onChoose = { url = it },
                    )
                }
                if (throughAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AccountPicker(
                        label = stringResource(if (type == "person") R.string.feeds_source_follow_with else R.string.feeds_source_read_with),
                        accounts = throughAccounts,
                        chosen = through,
                        onChoose = { through = it },
                    )
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_add),
        onConfirm = {
            val address = url.trim().let { value ->
                if (type == "rss" && !value.startsWith("http://") && !value.startsWith("https://")) "https://$value" else value
            }
            onAdd(address, type, through.takeIf { it.isNotEmpty() && throughAccounts.isNotEmpty() })
        },
        confirmEnabled = (!urlRequired && !chooseAccount) || url.isNotBlank(),
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !busy,
    )
}

/** One of the owner's accounts, chosen from a dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(
    label: String,
    accounts: List<PublishAccount>,
    chosen: String,
    onChoose: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val sorted = accounts.sortedWith(compareBy(NaturalCompare) { it.identifier })
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        MochiTextField(
            value = sorted.firstOrNull { it.id == chosen }?.identifier.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sorted.forEach { account ->
                MochiDropdownMenuItem(
                    text = { Text(account.identifier) },
                    onClick = {
                        onChoose(account.id)
                        open = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSourceDialog(
    source: Source,
    busy: Boolean,
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

    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.feeds_edit_source),
        content = {
            Column {
                MochiTextField(
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
                    MochiTextField(
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
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = {
            val credibilityValue = credibility.toInt()
            onSave(
                name.takeIf { it != source.name },
                credibilityValue.takeIf { value -> isRss && value != initialCredibility },
                transform.takeIf { it != source.transform }
            )
        },
        confirmLoading = busy,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !busy,
    )
}

@Composable
internal fun SuggestedCredibilityDialog(
    suggested: Int,
    busy: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.feeds_suggested_credibility_title),
        text = stringResource(R.string.feeds_suggested_credibility_body, LocalFormat.current.formatNumber(suggested)),
        confirmText = stringResource(R.string.feeds_suggested_credibility_accept),
        onConfirm = onAccept,
        confirmLoading = busy,
        dismissText = stringResource(R.string.feeds_suggested_credibility_keep),
        dismissEnabled = !busy,
    )
}

@Composable
private fun RemoveSourceDialog(
    source: Source,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRemove: (Boolean) -> Unit
) {
    var deletePosts by remember { mutableStateOf(false) }

    MochiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = stringResource(R.string.feeds_remove_source),
        content = {
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
        confirmText = stringResource(R.string.feeds_remove),
        onConfirm = { onRemove(deletePosts) },
        confirmLoading = busy,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
        dismissEnabled = !busy,
    )
}
