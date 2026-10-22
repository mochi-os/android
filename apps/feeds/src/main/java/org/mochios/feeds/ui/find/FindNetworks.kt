// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.find

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.util.isWebUrl
import org.mochios.android.util.providerName
import org.mochios.feeds.R
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Lookup
import org.mochios.feeds.model.Person
import org.mochios.feeds.ui.component.QuoteCard

/**
 * What a handle or link looked up on another network names: a person or
 * community with a way to follow them into one of the user's feeds, or a post
 * there with a way to follow its author.
 */
@Composable
internal fun LookupResult(
    lookup: Lookup,
    query: String,
    feeds: List<Feed>,
    busy: Boolean,
    onFollow: (Feed, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lookup.kind == "post") {
        val quote = lookup.quote ?: return
        val author = quote.author
        val target = author?.url?.ifEmpty { author.id } ?: ""
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuoteCard(quote)
            if (target.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FollowMenu(
                        label = stringResource(R.string.feeds_follow_named, author?.label.orEmpty()),
                        feeds = feeds,
                        busy = busy,
                        onFollow = { feed -> onFollow(feed, target) },
                    )
                }
            }
        }
        return
    }
    val name = lookup.name.ifEmpty { lookup.address }.ifEmpty { query }
    val details = listOfNotNull(
        lookup.address.takeIf { it.isNotEmpty() },
        lookup.network.takeIf { it.isNotEmpty() }?.let(::providerName),
        if (lookup.kind == "group") stringResource(R.string.feeds_community) else null,
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        EntityAvatar(name = name, src = lookup.avatar.takeIf { isWebUrl(it) }, seed = lookup.id.ifEmpty { query }, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FollowMenu(
            label = stringResource(R.string.feeds_follow),
            feeds = feeds,
            busy = busy,
            onFollow = { feed -> onFollow(feed, lookup.id.ifEmpty { query }) },
        )
    }
}

/** The user's contacts with a handle on another network, offered to follow. */
@Composable
internal fun PersonRow(
    person: Person,
    feeds: List<Feed>,
    busy: Boolean,
    onFollow: (Feed, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = person.name.ifEmpty { person.handle }
    val details = listOf(person.address.ifEmpty { person.handle }, providerName(person.network))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        EntityAvatar(name = name, src = person.avatar.takeIf { isWebUrl(it) }, seed = person.contact, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (person.followed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.feeds_following),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            FollowMenu(
                label = stringResource(R.string.feeds_follow),
                feeds = feeds,
                busy = busy,
                onFollow = { feed -> onFollow(feed, person.id.ifEmpty { person.handle }) },
            )
        }
    }
}

/** Follow into one of the user's own feeds, chosen from a menu; nothing where they own none. */
@Composable
internal fun FollowMenu(
    label: String,
    feeds: List<Feed>,
    busy: Boolean,
    onFollow: (Feed) -> Unit,
) {
    if (feeds.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        MochiButton(onClick = { open = true }, enabled = !busy) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MochiDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            feeds.forEach { feed ->
                MochiDropdownMenuItem(
                    text = { Text(feed.name) },
                    onClick = {
                        open = false
                        onFollow(feed)
                    },
                )
            }
        }
    }
}

/** The heading of the people the user knows. */
@Composable
internal fun PeopleHeading(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.feeds_people_you_know),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
