// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationCategory
import org.mochios.android.notifications.ordered
import org.mochios.android.push.notificationIconFor
import org.mochios.android.push.pushApp
import org.mochios.android.ui.theme.LocalSpacing

/**
 * One notification as a card: who or what it is from, its title, text and
 * age, and how many it stands for. [actions] trail the row, such as a picker
 * for the notification's category.
 */
@Composable
fun NotificationCard(
    notification: MochiNotification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val format = LocalFormat.current
    val spacing = LocalSpacing.current
    MochiCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(spacing.card(horizontal = 12.dp, vertical = 12.dp))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.gap(12.dp)),
            ) {
                // Always a leading circle so rows align: the sender's avatar,
                // or with no sender the icon of the app it came from.
                val icon = sourceIcon(notification)
                if (icon == null) {
                    EntityAvatar(
                        name = notification.sender,
                        src = personAvatarPath(notification.sender),
                        seed = notification.sender,
                        size = 36.dp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Decorative: the row's own text says what it is about.
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.gap(6.dp)),
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = notification.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = format.formatRelativeTime(notification.created),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (notification.count > 1) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = spacing.gap(6.dp)),
                    ) {
                        Text(
                            text = "×${format.formatNumber(notification.count)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                actions()
            }
        }
    }
}

/**
 * The tray icon of the app a notification with no sender came from, which its
 * row shows; null for one with a sender, whose row shows the person, as the
 * web's list does.
 */
@DrawableRes
internal fun sourceIcon(notification: MochiNotification): Int? =
    if (notification.sender.isNotBlank()) {
        null
    } else {
        notificationIconFor(pushApp(notification.app, notification.link))
    }

/**
 * The choices of a category menu: "Unassigned", then [categories] in reading
 * order, with [current] marked. Place it inside a dropdown menu's content.
 */
@Composable
fun NotificationCategoryItems(
    categories: List<NotificationCategory>,
    current: String?,
    onSelect: (String?) -> Unit,
) {
    // "No notifications" is the seeded id "0" and belongs at the end as the
    // opt-out; the rest read alphabetically, which is where a reader looks.
    val ordered = remember(categories) { categories.ordered() }
    MochiDropdownMenuItem(
        text = { Text(stringResource(R.string.notifications_category_unassigned)) },
        onClick = { onSelect(null) },
        selected = current == null,
    )
    for (category in ordered) {
        MochiDropdownMenuItem(
            text = { Text(category.shown) },
            onClick = { onSelect(category.id) },
            selected = current == category.id,
        )
    }
}
