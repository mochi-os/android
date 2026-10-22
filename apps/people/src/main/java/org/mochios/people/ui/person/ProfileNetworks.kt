// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.util.openWeb
import org.mochios.android.util.providerName
import org.mochios.people.R
import org.mochios.people.model.ProfileAccount
import org.mochios.people.model.ProfileHistory

/** The link a forum post or comment opens in Forums: the post, under its forum. */
internal fun forumLink(item: ProfileHistory): String =
    "forums/${item.fingerprint.ifBlank { item.forum }}/${item.post}"

/**
 * What a profile shows beyond the person's own words: their accounts on other
 * networks, each opening on its network, and their newest posts and comments
 * in public forums, each opening in Forums.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileNetworks(
    accounts: List<ProfileAccount>,
    history: List<ProfileHistory>,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val shown = accounts.filter { it.url.startsWith("https://") }
    if (shown.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (account in shown) {
                Text(
                    text = "${providerName(account.type)} · ${account.identifier}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { openWeb(context, account.url) }
                        .padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (history.isNotEmpty()) {
        val format = LocalFormat.current
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.people_person_forums),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            for (item in history) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLink(forumLink(item)) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (item.kind == "post") {
                            Icons.AutoMirrored.Outlined.Article
                        } else {
                            Icons.AutoMirrored.Outlined.Chat
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, end = 12.dp).size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.kind == "comment" && item.body.isNotBlank()) {
                            Text(
                                text = item.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${item.forumName} · ${format.formatTimestamp(item.created)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
