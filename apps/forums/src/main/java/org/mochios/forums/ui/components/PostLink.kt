// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.util.isWebUrl
import org.mochios.android.util.openWeb
import org.mochios.forums.R
import org.mochios.forums.model.Crosspost
import org.mochios.forums.model.Post
import org.mochios.forums.model.postEmbed

private val IMAGE = Regex("\\.(avif|gif|jpe?g|png|webp)$", RegexOption.IGNORE_CASE)

/**
 * Whether a post's link is an image, as every Lemmy image post's is: by the
 * media type its network gave, else by its address - the file's own, or the
 * one Lemmy's image proxy carries in its url parameter.
 */
internal fun linkImage(post: Post): Boolean {
    if (!isWebUrl(post.url)) return false
    val type = postEmbed(post.embed).type
    if (type.isNotEmpty()) return type.startsWith("image/")
    val uri = runCatching { Uri.parse(post.url) }.getOrNull() ?: return false
    val path = uri.path.orEmpty()
    if (IMAGE.containsMatchIn(path)) return true
    if (!path.endsWith("/image_proxy")) return false
    val proxied = runCatching { uri.getQueryParameter("url") }.getOrNull() ?: return false
    return IMAGE.containsMatchIn(Uri.parse(proxied).path.orEmpty())
}

/** A link's host as a card names it, without its www. */
internal fun linkHost(url: String): String =
    runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotEmpty() } ?: url

/**
 * The link a post carries, as a card: its host, what the page says of itself
 * where the server could read it, and its picture. The whole card opens the
 * link; a card is a link to somewhere else, so it never routes inside Mochi.
 * [compact] is the card's list form: one line, a small picture.
 */
@Composable
internal fun PostLinkCard(post: Post, compact: Boolean = false, modifier: Modifier = Modifier) {
    if (!isWebUrl(post.url)) return
    val context = LocalContext.current
    val embed = postEmbed(post.embed)
    // An image link is the post's picture: shown as itself, as its network
    // shows it, rather than as a card naming its address. A list shows the
    // network's smaller copy where it gave one.
    if (linkImage(post)) {
        AsyncImage(
            model = if (compact && isWebUrl(post.thumbnail)) post.thumbnail else post.url,
            contentDescription = embed.title.ifEmpty { null },
            contentScale = if (compact) ContentScale.Crop else ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.height(192.dp) else Modifier.heightIn(max = 560.dp))
                .clip(RoundedCornerShape(10.dp))
                .clickable { openWeb(context, post.url) }
                .testTag("post-link-image"),
        )
        return
    }
    Row(
        verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { openWeb(context, post.url) }
            .padding(8.dp),
    ) {
        if (isWebUrl(post.thumbnail)) {
            AsyncImage(
                model = post.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(if (compact) 48.dp else 96.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = embed.site.ifEmpty { linkHost(post.url) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = embed.title.ifEmpty { post.url },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (embed.title.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && embed.description.isNotEmpty()) {
                Text(
                    text = embed.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The mark of a sensitive post beside its title. */
@Composable
internal fun SensitiveBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            Icons.Outlined.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.forums_sensitive),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * What a sensitive post shows - its text, its link and its attachments - held
 * behind a cover until the reader asks to see it.
 */
@Composable
internal fun SensitiveCover(sensitive: Boolean, key: String, content: @Composable () -> Unit) {
    var shown by rememberSaveable(key) { mutableStateOf(false) }
    if (!sensitive || shown) {
        content()
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.forums_sensitive_cover),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        MochiOutlinedButton(onClick = { shown = true }) {
            Text(stringResource(R.string.forums_sensitive_show))
        }
    }
}

/** The other posts here carrying the same link, each opening its post. */
@Composable
internal fun Crossposts(
    crossposts: List<Crosspost>,
    onOpen: (Crosspost) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (crossposts.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.forums_crossposts),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        crossposts.forEach { crosspost ->
            Text(
                text = crosspost.forumName.ifEmpty { crosspost.title },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(crosspost) },
            )
        }
    }
}
