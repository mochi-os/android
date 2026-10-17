// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.theme.LocalEntityRadius

/** Padding around a list of [MarketListRow]s, as People lays out its `EntityListRow`s. */
val MarketListPadding = PaddingValues(12.dp)

/** Gap between [MarketListRow]s. */
val MarketListSpacing = Arrangement.spacedBy(4.dp)

/**
 * The Market's list row, built on the look of lib's `EntityListRow`: a rounded
 * tile with optional [leading] art, [title] over a [subtitle] and an optional
 * [preview] line, a row of [badges], [trailing] content on the right (usually
 * the price) and an optional [menu]. [bold] sets the title and preview in the
 * strong colour, as an unread thread does.
 */
@Composable
fun MarketListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    preview: String? = null,
    bold: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    badges: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable ColumnScope.() -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    val secondary = if (contentColor == MaterialTheme.colorScheme.onSurface) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        contentColor
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalEntityRadius.current))
            .background(containerColor)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (leading != null) 12.dp else 16.dp,
                top = 12.dp,
                bottom = 12.dp,
                end = if (menu != null) 4.dp else 16.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (leading != null) 12.dp else 0.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!preview.isNullOrBlank()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (bold) contentColor else secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (badges != null) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = badges,
                    )
                }
            }
            if (trailing != null) {
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = trailing,
                )
            }
            menu?.invoke()
        }
    }
}

/** A price or amount on the right of a [MarketListRow]. */
@Composable
fun MarketListPrice(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
    )
}

/** A small note under the price on the right of a [MarketListRow]. */
@Composable
fun MarketListNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** A person's avatar as a row's leading art, at the size the inbox has always used. */
@Composable
fun PersonThumbnail(name: String, account: String?) {
    EntityAvatar(
        name = name,
        src = account?.takeIf { id -> id.isNotBlank() }
            ?.let { id -> "/market/-/user/$id/asset/avatar" },
        seed = account,
        size = 40.dp,
    )
}
