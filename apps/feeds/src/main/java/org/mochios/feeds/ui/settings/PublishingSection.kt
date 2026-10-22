// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.Section
import org.mochios.android.util.destination
import org.mochios.android.util.providerName
import org.mochios.android.util.webUri
import org.mochios.feeds.R
import org.mochios.feeds.model.Filter
import org.mochios.feeds.model.PublishAccount
import org.mochios.android.R as MochiR

/** Tell the owner what a publishing or filter change came to. */
@Composable
internal fun PublishingEvents(viewModel: PublishingViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val format = LocalFormat.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is PublishEvent.Failure -> (event.error as? MochiError.Unknown)?.let { unknown ->
                    unknown.message ?: resources.getString(event.fallback)
                } ?: event.error.userMessage()
                is PublishEvent.Deleted ->
                    resources.getQuantityString(R.plurals.feeds_copies_deleted, event.count, format.formatNumber(event.count))
                is PublishEvent.Generator ->
                    resources.getString(if (event.on) R.string.feeds_generator_on else R.string.feeds_generator_off)
                is PublishEvent.Imported -> when {
                    event.kind == "follows" && event.more ->
                        resources.getString(R.string.feeds_import_sources_more, format.formatNumber(event.count))
                    event.kind == "follows" ->
                        resources.getQuantityString(R.plurals.feeds_import_sources, event.count, format.formatNumber(event.count))
                    event.more -> resources.getString(R.string.feeds_imported_more, format.formatNumber(event.count))
                    else -> resources.getString(R.string.feeds_imported, format.formatNumber(event.count))
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Where a public feed publishes: the owner's accounts on other networks, each
 * switched on or off for the feed. Switching one off offers to delete the
 * copies already there, keeping them by default. Each account's history can
 * be imported into the feed, and its archive downloaded.
 */
@Composable
internal fun PublishingSection(viewModel: PublishingViewModel) {
    val state by viewModel.publish.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val kept by viewModel.kept.collectAsState()
    val backups by viewModel.backups.collectAsState()
    val accounts = viewModel.accounts(state)
    if (accounts.isEmpty()) return
    val publish = state?.publish.orEmpty()
    val copies = state?.copies.orEmpty()
    val generators = state?.generators.orEmpty()
    val format = LocalFormat.current
    val context = LocalContext.current

    Section(title = stringResource(R.string.feeds_publish_to)) {
        accounts.forEach { account ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.identifier,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val count = copies[account.id] ?: 0
                    Text(
                        text = buildString {
                            append(providerName(account.type))
                            if (count > 0) {
                                append(" · ")
                                append(pluralStringResource(R.plurals.feeds_copies, count, format.formatNumber(count)))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AccountMenu(
                    account = account,
                    generator = generators[account.id]?.isNotEmpty() == true,
                    onImport = { kind -> viewModel.importFrom(account, kind) },
                    onBackups = { viewModel.openBackups(account) },
                    onGenerator = { on -> viewModel.generator(account, on) },
                )
                val label = stringResource(R.string.feeds_publish_to_account, account.identifier)
                Switch(
                    checked = account.id in publish,
                    enabled = busy != account.id,
                    onCheckedChange = { on -> viewModel.toggle(account, on) },
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
    }

    kept?.let { offer ->
        MochiAlertDialog(
            onDismissRequest = { viewModel.keep() },
            title = stringResource(R.string.feeds_copies_delete_title, offer.account.identifier),
            text = stringResource(R.string.feeds_copies_delete_message, format.formatNumber(offer.count)),
            confirmText = stringResource(R.string.feeds_copies_delete),
            onConfirm = { viewModel.forget(offer.account) },
            destructive = true,
            dismissText = stringResource(R.string.feeds_copies_keep),
        )
    }

    backups?.let { shown ->
        BackupsDialog(
            backups = shown,
            onDownload = { name ->
                val (url, token) = viewModel.backupFile(shown.account, name)
                startBackupDownload(context, url, name, token)
            },
            onDismiss = { viewModel.closeBackups() },
        )
    }
}

@Composable
private fun AccountMenu(
    account: PublishAccount,
    generator: Boolean,
    onImport: (String) -> Unit,
    onBackups: () -> Unit,
    onGenerator: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        MochiIconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(MochiR.string.common_more_options))
        }
        MochiDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                "posts" to R.string.feeds_import_posts,
                "bookmarks" to R.string.feeds_import_bookmarks,
                "follows" to R.string.feeds_import_follows_sources,
            ).forEach { (kind, label) ->
                MochiDropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    onClick = {
                        open = false
                        onImport(kind)
                    },
                )
            }
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.feeds_backups)) },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                onClick = {
                    open = false
                    onBackups()
                },
            )
            // A feed published as a Bluesky custom feed from a hosted Bluesky
            // account: a generator record there, which Bluesky asks this
            // server for the feed's posts through.
            if (account.hosted && account.type == "bluesky") {
                MochiDropdownMenuItem(
                    text = {
                        Text(stringResource(if (generator) R.string.feeds_generator_stop else R.string.feeds_generator_start))
                    },
                    leadingIcon = { Icon(Icons.Outlined.RssFeed, contentDescription = null) },
                    onClick = {
                        open = false
                        onGenerator(!generator)
                    },
                )
            }
        }
    }
}

/** The archive files kept for an account, each a download. */
@Composable
private fun BackupsDialog(
    backups: AccountBackups,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.feeds_backups_title, backups.account.identifier),
        content = {
            val files = backups.files
            when {
                files == null -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                files.isEmpty() -> Text(
                    text = stringResource(R.string.feeds_backups_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    files.forEach { name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDownload(name) }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_close),
        onConfirm = onDismiss,
    )
}

/**
 * Hand an archive file to [DownloadManager]. The Authorization header is set
 * inline: DownloadManager runs outside the app process and never sees the
 * interceptor stack.
 */
private fun startBackupDownload(context: Context, url: String, name: String, token: String?) {
    val uri = webUri(url) ?: return
    val safe = name.trim().replace(Regex("[/\\\\ ]"), "_").ifEmpty { "backup" }
    val request = DownloadManager.Request(uri)
        .setTitle(safe)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .destination(context, safe)
    if (!token.isNullOrBlank()) request.addRequestHeader("Authorization", "Bearer $token")
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}

/**
 * Keyword filters on a feed: a post naming one of the phrases is hidden from
 * the reader who set it, on their own feeds and the ones they subscribe to
 * alike.
 */
@Composable
internal fun FiltersSection(viewModel: PublishingViewModel) {
    val filters by viewModel.filters.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var phrase by remember { mutableStateOf("") }
    Section(title = stringResource(R.string.feeds_filters)) {
        filters.forEach { filter ->
            FilterRow(filter = filter, busy = busy == filter.id, onRemove = { viewModel.removeFilter(filter) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MochiTextField(
                value = phrase,
                onValueChange = { phrase = it.take(100) },
                label = { Text(stringResource(R.string.feeds_filter_phrase)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            MochiButton(
                onClick = {
                    viewModel.addFilter(phrase)
                    phrase = ""
                },
                enabled = phrase.isNotBlank() && busy != "filter",
            ) {
                if (busy == "filter") {
                    CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(MochiR.string.common_add))
            }
        }
    }
}

@Composable
private fun FilterRow(filter: Filter, busy: Boolean, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(filter.phrase, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        MochiIconButton(onClick = onRemove, enabled = !busy) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.feeds_filter_remove, filter.phrase),
                )
            }
        }
    }
}
