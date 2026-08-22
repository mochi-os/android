// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Universal drawer entry each app adapts its own row type into, so one
 * [MochiListDrawer] renders every app's drawer. The leading slot resolves
 * [avatarUrl] first, then a seeded [EntityIconCircle], then the plain [icon].
 * [section] groups consecutive items under a heading; null keeps the list flat.
 */
data class DrawerItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val unread: Int = 0,
    val icon: ImageVector? = null,
    val trailingIcon: ImageVector? = null,
    val avatarUrl: String? = null,
    val seed: String? = null,
    val section: String? = null,
)

/**
 * Slide-in left drawer for an app's item list: optional header, a pinned
 * [allItem], the scrollable [items], and a bottom [actions] slot that stays
 * put. [emptyState] replaces the list when [items] is empty. The caller owns
 * [drawerState] so the host's TopAppBar can open it too.
 */
@Composable
fun MochiListDrawer(
    drawerState: DrawerState,
    items: List<DrawerItem>,
    selectedId: String?,
    onItemClick: (DrawerItem) -> Unit,
    header: (@Composable () -> Unit)? = null,
    allItem: DrawerItem? = null,
    actions: (@Composable () -> Unit)? = null,
    emptyState: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    if (header != null || allItem != null) {
                        header?.invoke()
                        if (allItem != null) {
                            DrawerItemRow(
                                item = allItem,
                                isSelected = selectedId == allItem.id,
                                onClick = { onItemClick(allItem) },
                                pinned = true,
                            )
                        }
                        HorizontalDivider()
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        if (items.isEmpty() && emptyState != null) {
                            item(key = "empty") { emptyState() }
                        }
                        // Section headings are emitted inline rather than
                        // taking a separate grouped-items parameter, so a
                        // flat list (section == null throughout) stays a
                        // plain run of rows with no extra nesting.
                        var previousSection: String? = null
                        var isFirstRow = true
                        for (entry in items) {
                            val section = entry.section
                            if (section != null && section != previousSection) {
                                // Snapshot into a val: `item {}` bodies run at
                                // composition time, long after this loop has
                                // finished, so reading the `isFirstRow` var
                                // inside one would see its final value and
                                // draw a divider above every heading.
                                val showDivider = !isFirstRow
                                item(key = "section:$section") {
                                    DrawerSectionHeader(
                                        title = section,
                                        // The divider separates this group
                                        // from the one above, so the topmost
                                        // heading doesn't get one — the
                                        // header slot already drew it.
                                        showDivider = showDivider,
                                    )
                                }
                            }
                            previousSection = section
                            isFirstRow = false
                            item(key = entry.id) {
                                DrawerItemRow(
                                    item = entry,
                                    isSelected = selectedId == entry.id,
                                    onClick = { onItemClick(entry) },
                                )
                            }
                        }
                    }

                    if (actions != null) {
                        HorizontalDivider()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            actions()
                        }
                    }
                }
            }
        },
        content = content,
    )
}

/**
 * The drawer's headline: the app's own name above the item list.
 */
@Composable
fun DrawerTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
    )
}

/**
 * Group heading inside the item list, drawn where a run of
 * [DrawerItem.section] values changes. Quieter than [DrawerTitle], and
 * indented to the same 24dp as the rows it introduces.
 */
@Composable
private fun DrawerSectionHeader(title: String, showDivider: Boolean) {
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * Compact action row for the drawer's bottom [MochiListDrawer.actions] slot.
 * No enforced min-height or wide padding, so actions sit tight together.
 */
@Composable
fun DrawerActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DrawerItemRow(
    item: DrawerItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    pinned: Boolean = false,
) {
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val background = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .then(
                // Selected rows carry a thin accent bar flush to the start edge.
                if (isSelected) {
                    Modifier.drawBehind {
                        drawRect(
                            color = accentColor,
                            size = Size(width = 4.dp.toPx(), height = size.height),
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            !item.avatarUrl.isNullOrBlank() -> {
                EntityAvatar(
                    name = item.title,
                    src = item.avatarUrl,
                    seed = item.seed ?: item.id,
                    size = 32.dp,
                )
                Spacer(modifier = Modifier.size(12.dp))
            }

            // A seed with no icon and no avatar URL still gets a circle:
            // EntityAvatar falls back to seeded initials, which is what a
            // person-shaped row (a chess opponent, say) wants when the
            // avatar asset path can't be built.
            item.seed != null && item.icon == null -> {
                EntityAvatar(
                    name = item.title,
                    src = null,
                    seed = item.seed,
                    size = 32.dp,
                )
                Spacer(modifier = Modifier.size(12.dp))
            }

            item.seed != null && item.icon != null && !pinned -> {
                EntityIconCircle(
                    seed = item.seed,
                    icon = item.icon,
                    size = 32.dp,
                )
                Spacer(modifier = Modifier.size(12.dp))
            }

            item.icon != null -> {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor,
                    )
                    // Small hollow ring marking the aggregate "All" item.
                    if (pinned) {
                        Box(
                            modifier = Modifier
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(9.dp)
                                .border(width = 1.5.dp, color = contentColor, shape = CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.trailingIcon != null) {
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                imageVector = item.trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.unread > 0) {
            Spacer(modifier = Modifier.size(8.dp))
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ) {
                Text(text = item.unread.toString())
            }
        }
    }
}
