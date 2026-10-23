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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.providerName
import org.mochios.forums.R
import org.mochios.forums.model.NetworkAccount

/**
 * The Find screen's ways in from other networks: join a community elsewhere
 * as one of the user's hosted fediverse accounts, and read in the communities
 * and settings of a linked Lemmy account. Each shows only where the user has
 * such an account.
 */
@Composable
internal fun CommunitiesSections(
    viewModel: CommunitiesViewModel,
    onMessage: suspend (String) -> Unit,
    onOpenForum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hosted by viewModel.hosted.collectAsState()
    val lemmy by viewModel.lemmy.collectAsState()
    val busy by viewModel.busy.collectAsState()
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
    if (hosted.isEmpty() && lemmy.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (hosted.isNotEmpty()) {
            JoinCommunity(accounts = hosted, busy = busy == "join", onJoin = { address, account -> viewModel.join(address, account) })
        }
        if (lemmy.isNotEmpty()) {
            LemmyAccounts(
                accounts = lemmy,
                busy = busy,
                onAdd = { viewModel.add(it) },
                onImport = { viewModel.import(it) },
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A community on another server joined by its address, as one of the user's hosted accounts. */
@Composable
internal fun JoinCommunity(
    accounts: List<NetworkAccount>,
    busy: Boolean,
    onJoin: (address: String, account: String) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    val account = accounts.firstOrNull { it.id == chosen } ?: accounts.first()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(stringResource(R.string.forums_join_community))
        MochiTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.forums_community_address)) },
            placeholder = { Text(stringResource(R.string.forums_community_address_hint)) },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Which hosted account joins, where there is more than one.
            if (accounts.size > 1) {
                Box(modifier = Modifier.weight(1f)) {
                    MochiTextButton(onClick = { open = true }, enabled = !busy) {
                        Text(account.identifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    MochiDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        accounts.forEach { item ->
                            MochiDropdownMenuItem(
                                text = { Text(item.identifier) },
                                onClick = {
                                    open = false
                                    chosen = item.id
                                },
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            MochiOutlinedButton(
                onClick = {
                    onJoin(address, account.id)
                    address = ""
                },
                enabled = address.isNotBlank() && !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(stringResource(R.string.forums_join))
            }
        }
    }
}

/** The user's linked Lemmy accounts, whose communities and settings are read in. */
@Composable
internal fun LemmyAccounts(
    accounts: List<NetworkAccount>,
    busy: String?,
    onAdd: (NetworkAccount) -> Unit,
    onImport: (NetworkAccount) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading(stringResource(R.string.forums_communities_elsewhere))
        accounts.forEach { account ->
            val working = busy == account.id
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(account.identifier, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    providerName(account.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MochiOutlinedButton(onClick = { onImport(account) }, enabled = busy == null) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.forums_import_settings))
                    }
                    MochiButton(onClick = { onAdd(account) }, enabled = busy == null) {
                        if (working) {
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
