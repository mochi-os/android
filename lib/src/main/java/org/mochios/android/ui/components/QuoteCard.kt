// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.android.util.isWebUrl
import org.mochios.android.util.openWeb
import org.mochios.android.util.providerName

/**
 * A post on another network quoted in a Mochi post or message: who wrote it,
 * its text and its first picture, the whole card a link to the post. The
 * server reads the quote when the post is written and keeps it with the post,
 * so every reader sees what the writer saw. Only web links are followed, since
 * a quote arrives from another server.
 */
@Composable
fun QuoteCard(
    network: String,
    url: String,
    text: String,
    image: String,
    name: String,
    address: String,
    avatar: String,
    seed: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = name.ifEmpty { address }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .then(if (isWebUrl(url)) Modifier.clickable { openWeb(context, url) } else Modifier)
            .padding(12.dp),
    ) {
        if (label.isNotEmpty() || network.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (label.isNotEmpty()) {
                    EntityAvatar(
                        name = label,
                        src = avatar.takeIf { isWebUrl(it) },
                        seed = seed.ifEmpty { address },
                        size = 20.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (address.isNotEmpty() && name.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (network.isNotEmpty()) {
                    Text(
                        text = providerName(network),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isWebUrl(image)) {
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}
