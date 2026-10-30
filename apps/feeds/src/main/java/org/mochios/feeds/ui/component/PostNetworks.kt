// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.QuoteCard as LibQuoteCard
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.util.isWebUrl
import org.mochios.android.util.openWeb
import org.mochios.android.util.providerName
import org.mochios.feeds.R
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PostCopy
import org.mochios.feeds.model.PostEmoji
import org.mochios.feeds.model.PostMedia
import org.mochios.feeds.model.PostPoll
import org.mochios.feeds.model.PostQuote

/** Whether the post's badges have anything to say: pinned, or not public. */
internal fun hasBadges(post: Post): Boolean =
    post.pinned > 0 || post.visibility == "followers" || post.visibility == "unlisted"

/**
 * What a post says of itself above its text: who boosted it here, the person
 * on another network who wrote it, that it is pinned, and that it is not
 * public.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostHeadline(post: Post, modifier: Modifier = Modifier) {
    val person = post.data?.person
    val boost = post.data?.boost
    if (person == null && boost == null && !hasBadges(post)) return
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        boost?.let { booster ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.then(
                    if (isWebUrl(booster.page)) Modifier.clickable { openWeb(context, booster.page) } else Modifier,
                ),
            ) {
                Icon(
                    Icons.Outlined.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.feeds_boosted_by, booster.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        person?.let { author ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityAvatar(
                    name = author.label,
                    src = author.avatar.takeIf { isWebUrl(it) },
                    seed = author.id.ifEmpty { author.address },
                    size = 28.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = author.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.then(
                            if (isWebUrl(author.page)) Modifier.clickable { openWeb(context, author.page) } else Modifier,
                        ),
                    )
                    if (author.address.isNotEmpty() && author.address != author.label) {
                        Text(
                            text = author.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (hasBadges(post)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (post.pinned > 0) {
                    Badge(Icons.Outlined.PushPin, stringResource(R.string.feeds_pinned))
                }
                if (post.visibility == "followers") {
                    Badge(Icons.Outlined.Lock, stringResource(R.string.feeds_visibility_followers))
                }
                if (post.visibility == "unlisted") {
                    Badge(Icons.Outlined.VisibilityOff, stringResource(R.string.feeds_visibility_unlisted))
                }
            }
        }
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A post's content warning: a quiet strip, opened or closed by a tap
 * anywhere on it. The caller hides the post's text, media and poll until
 * [revealed].
 */
@Composable
internal fun PostWarning(
    warning: String,
    revealed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = warning,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(if (revealed) R.string.feeds_hide_post else R.string.feeds_show_post),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Where else a post is: its copies on other networks, each a link, and how often it was boosted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostCopies(copies: List<PostCopy>, boosts: Int, modifier: Modifier = Modifier) {
    if (copies.isEmpty() && boosts <= 0) return
    val context = LocalContext.current
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        copies.forEach { copy ->
            Text(
                text = stringResource(R.string.feeds_also_on, providerName(copy.network)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.then(
                    if (isWebUrl(copy.url)) Modifier.clickable { openWeb(context, copy.url) } else Modifier,
                ),
            )
        }
        if (boosts > 0) {
            Text(
                text = pluralStringResource(R.plurals.feeds_boosted_times, boosts, LocalFormat.current.formatNumber(boosts)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A post on another network quoted in a post: who wrote it, its text and its
 * first picture, the whole card a link to the post.
 */
@Composable
internal fun QuoteCard(quote: PostQuote, modifier: Modifier = Modifier) {
    val author = quote.author
    LibQuoteCard(
        network = quote.network,
        url = quote.url,
        text = quote.text,
        image = quote.image,
        name = author?.name.orEmpty(),
        address = author?.address.orEmpty(),
        avatar = author?.avatar.orEmpty(),
        seed = author?.id.orEmpty(),
        modifier = modifier,
    )
}

/**
 * The media of a post from another network, by their addresses there. A
 * sensitive post shows each picture's blurhash until the reader chooses to see
 * it; a picture with no blurhash is simply hidden until then.
 */
@Composable
internal fun PostMediaGrid(media: List<PostMedia>, sensitive: Boolean, modifier: Modifier = Modifier) {
    val pictures = media.filter { (it.type == "image" || it.type == "video") && isWebUrl(it.url) }
    val files = media.filter { it.type != "image" && it.type != "video" && isWebUrl(it.url) }
    if (pictures.isEmpty() && files.isEmpty()) return
    var shown by rememberSaveable(sensitive) { mutableStateOf(!sensitive) }
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pictures.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shown) {
                            AsyncImage(
                                model = item.url,
                                contentDescription = item.name.ifEmpty { null },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { openWeb(context, item.url) },
                            )
                        } else {
                            BlurhashImage(item.blurhash)
                            MochiOutlinedButton(onClick = { shown = true }) {
                                Icon(
                                    Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.feeds_show_sensitive_media))
                            }
                        }
                    }
                }
                if (row.size == 1 && pictures.size > 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        files.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { openWeb(context, item.url) },
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.name.ifEmpty { item.url },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BlurhashImage(hash: String) {
    val bitmap = remember(hash) {
        decodeBlurhash(hash, 32, 32)?.let { pixels ->
            Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * A post's poll: its options with their share of the votes, the reader's own
 * choice, and a vote while it is open.
 */
@Composable
internal fun PostPollView(
    poll: PostPoll,
    voted: List<Int>,
    canVote: Boolean,
    busy: Boolean,
    onVote: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis() / 1000,
) {
    var chosen by remember(voted) { mutableStateOf(voted) }
    // A reader who may still vote can look at the results first, and go back.
    var peeking by remember { mutableStateOf(false) }
    val closed = poll.expires in 1..now
    val votable = !closed && voted.isEmpty() && canVote
    val results = !votable || peeking
    val total = poll.options.sumOf { it.votes }
    val format = LocalFormat.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        poll.options.forEachIndexed { index, option ->
            if (results) {
                val share = if (total > 0) option.votes.toFloat() / total else 0f
                val mine = index in voted
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (mine) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(option.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = format.formatNumber(Math.round(share * 100)) + "%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { share },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {},
                    )
                }
            } else {
                val selected = index in chosen
                val toggle = {
                    chosen = if (poll.multiple) {
                        if (selected) chosen - index else chosen + index
                    } else {
                        listOf(index)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (poll.multiple) {
                                Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { toggle() })
                            } else {
                                Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = toggle)
                            },
                        ),
                ) {
                    if (poll.multiple) {
                        Checkbox(checked = selected, onCheckedChange = null)
                    } else {
                        RadioButton(selected = selected, onClick = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val votes = pluralStringResource(R.plurals.feeds_poll_votes, poll.voters, format.formatNumber(poll.voters))
            val state = when {
                poll.expires <= 0 -> null
                closed -> stringResource(R.string.feeds_poll_closed)
                else -> stringResource(R.string.feeds_poll_closes, format.formatTimestamp(poll.expires))
            }
            Text(
                text = listOfNotNull(votes, state).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (votable) {
                MochiTextButton(onClick = { peeking = !peeking }) {
                    Text(stringResource(if (peeking) R.string.feeds_poll_hide_results else R.string.feeds_poll_see_results))
                }
                if (!peeking) {
                    MochiButton(onClick = { onVote(chosen) }, enabled = chosen.isNotEmpty() && !busy) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.feeds_poll_vote))
                    }
                }
            }
        }
    }
}

private val EMOJI = Regex(":([A-Za-z0-9_]+):")

/**
 * A post's text with each custom emoji it names by `:name:` drawn as its
 * picture, a line high. Names the post does not carry stay as written.
 */
internal fun withEmoji(text: String, emoji: List<PostEmoji>?): String {
    if (emoji.isNullOrEmpty()) return text
    val pictures = emoji.filter { it.name.isNotEmpty() && isWebUrl(it.url) }.associate { it.name to it.url }
    if (pictures.isEmpty()) return text
    return EMOJI.replace(text) { match ->
        val url = pictures[match.groupValues[1]] ?: return@replace match.value
        val escaped = url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
        "<img src=\"$escaped\" alt=\"${match.value}\" width=\"1.2em\" height=\"1.2em\">"
    }
}
