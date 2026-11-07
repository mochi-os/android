// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.find

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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.util.providerName
import org.mochios.forums.R
import org.mochios.forums.model.CommunityFound
import org.mochios.forums.model.NetworkAccount
import org.mochios.forums.model.communityHandle

/**
 * What may name a community elsewhere: !name@host or @name@host, name@host,
 * or a link that is not a Mochi forum's.
 */
internal fun looksLikeCommunity(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty() || q.any { it.isWhitespace() } || q.startsWith("mochi:", ignoreCase = true) || "/forums/" in q) return false
    return Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(q) || Regex("^[@!]?[^@/!]+@[^@/]+\\.[^@/]+$").matches(q)
}

/**
 * The Find screen's ways in from other networks: the community a search
 * names, joined as one of the user's hosted fediverse accounts, and, while
 * nothing is searched, the communities and settings of a linked Lemmy account
 * read in. Each shows only where the user has such an account.
 */
@Composable
internal fun CommunitiesSections(
    viewModel: CommunitiesViewModel,
    onMessage: suspend (String) -> Unit,
    onOpenForum: (String) -> Unit,
    modifier: Modifier = Modifier,
    typed: String? = null,
) {
    val hosted by viewModel.hosted.collectAsState()
    val lemmy by viewModel.lemmy.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val found by viewModel.found.collectAsState()
    val resources = LocalResources.current
    val format = LocalFormat.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CommunitiesEvent.Failure -> onMessage(
                    (event.error as? MochiError.Unknown)?.let { it.message ?: resources.getString(event.fallback) }
                        ?: event.error.userMessage(),
                )
                is CommunitiesEvent.Joined -> {
                    onMessage(resources.getString(if (event.already) R.string.forums_already_subscribed else R.string.forums_joined))
                    if (event.forum.isNotEmpty()) onOpenForum(event.forum)
                }
                is CommunitiesEvent.Added -> onMessage(
                    resources.getQuantityString(R.plurals.forums_communities_added, event.answer.added, format.formatNumber(event.answer.added)),
                )
                is CommunitiesEvent.Imported -> onMessage(
                    resources.getString(
                        if (event.answer.more) R.string.forums_imported_settings_more else R.string.forums_imported_settings,
                        format.formatNumber(event.answer.communities),
                        format.formatNumber(event.answer.blocks),
                        format.formatNumber(event.answer.saved),
                    ),
                )
            }
        }
    }
    if (typed != null) {
        if (hosted.isEmpty() || found.query != typed) return
        val community = found.community
        if (community != null) {
            CommunityResult(
                community = community,
                accounts = hosted,
                busy = "join" in busy,
                onJoin = { account -> viewModel.join(community.id, account) },
                modifier = modifier,
            )
        } else if (found.loading) {
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        return
    }
    if (lemmy.isEmpty()) return
    Column(modifier = modifier) {
        LemmyAccounts(
            accounts = lemmy,
            busy = busy,
            onAdd = { viewModel.add(it) },
            onImport = { viewModel.import(it) },
        )
    }
}

/** Whether the Find screen shows something from elsewhere for a search: the community it names, or its lookup on its way. */
internal fun CommunityLookup.answers(query: String): Boolean = query.isNotEmpty() && this.query == query && (loading || community != null)

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * The community a search names, as a result like the forums found here, with
 * Join; where the user has more than one hosted account, Join asks which.
 */
@Composable
internal fun CommunityResult(
    community: CommunityFound,
    accounts: List<NetworkAccount>,
    busy: Boolean,
    onJoin: (account: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntityAvatar(
            name = community.name,
            src = community.avatar.ifBlank { null },
            seed = community.id,
            size = 44.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = community.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (community.handle.isNotBlank()) {
                Text(
                    text = communityHandle(community.handle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box {
            MochiButton(
                onClick = { if (accounts.size == 1) onJoin(accounts.first().id) else open = true },
                enabled = !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.forums_join))
                }
            }
            MochiDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                accounts.forEach { item ->
                    MochiDropdownMenuItem(
                        text = { Text(item.identifier) },
                        onClick = {
                            open = false
                            onJoin(item.id)
                        },
                    )
                }
            }
        }
    }
}

/** The user's linked Lemmy accounts, whose communities and settings are read in. */
@Composable
internal fun LemmyAccounts(
    accounts: List<NetworkAccount>,
    busy: Set<String>,
    onAdd: (NetworkAccount) -> Unit,
    onImport: (NetworkAccount) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(stringResource(R.string.forums_communities_elsewhere))
        accounts.forEach { account ->
            // The button tapped spins; the account's other one waits, as both write its communities.
            val adding = lemmyKey("add", account.id) in busy
            val importing = lemmyKey("import", account.id) in busy
            val working = adding || importing
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(account.identifier, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    providerName(account.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MochiOutlinedButton(onClick = { onImport(account) }, enabled = !working) {
                        if (importing) {
                            CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.forums_import_settings))
                    }
                    MochiButton(onClick = { onAdd(account) }, enabled = !working) {
                        if (adding) {
                            CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.forums_communities_add))
                    }
                }
            }
        }
    }
}
