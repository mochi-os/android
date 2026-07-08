// Copyright © 2026 Mochi OÜ
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
import androidx.compose.foundation.lazy.items
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
 * Universal entry for an item rendered inside the feature drawer.
 *
 * Feature modules adapt their domain row type (Feed, Chat, Forum, Project)
 * into this shape so a single [FeatureListDrawer] composable can render every
 * feature's drawer. Keeps icon + unread badge + secondary subtitle as
 * common columns; anything richer (e.g. avatars from an entity asset URL)
 * can be wired by overriding [icon] with a custom composable per feature in
 * a future iteration.
 */
data class FeatureDrawerItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val unread: Int = 0,
    val icon: ImageVector? = null,
    val trailingIcon: ImageVector? = null,
)

/**
 * Slide-in left drawer for the per-feature item list (chats, feeds, forums,
 * projects). Matches the mobile-web pattern of hiding the list behind a
 * hamburger so the cold-start path lands directly on the last-viewed
 * detail screen.
 *
 * Drawer layout (top → bottom):
 *   - Header slot (optional: feature title, account picker, etc.)
 *   - Optional "All" pinned item rendered first when [allItem] is non-null.
 *   - Scrollable [items] list, divider above it.
 *   - Bottom [actions] slot for feature-level actions (Find, Add, Logout,
 *     Settings, RSS export, ...). Stays visible regardless of scroll.
 *
 * The caller owns the [drawerState] so the host screen can also wire the
 * hamburger button in its TopAppBar:
 *
 *   val drawerState = rememberDrawerState(DrawerValue.Closed)
 *   val scope = rememberCoroutineScope()
 *   IconButton(onClick = { scope.launch { drawerState.open() } }) { ... }
 */
@Composable
fun FeatureListDrawer(
    drawerState: DrawerState,
    items: List<FeatureDrawerItem>,
    selectedId: String?,
    onItemClick: (FeatureDrawerItem) -> Unit,
    header: (@Composable () -> Unit)? = null,
    allItem: FeatureDrawerItem? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    if (header != null) {
                        header()
                        HorizontalDivider()
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        if (allItem != null) {
                            item(key = "__all__") {
                                DrawerItemRow(
                                    item = allItem,
                                    isSelected = selectedId == allItem.id,
                                    onClick = { onItemClick(allItem) },
                                    pinned = true,
                                )
                            }
                        }
                        items(items, key = { it.id }) { it ->
                            DrawerItemRow(
                                item = it,
                                isSelected = selectedId == it.id,
                                onClick = { onItemClick(it) },
                            )
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
 * Compact action row for the drawer's bottom [FeatureListDrawer.actions] slot
 * (Find, Create, Logout, ...). Unlike Material's [androidx.compose.material3.ListItem]
 * it carries no enforced min-height or wide content padding, so the actions sit
 * tight together and align with the drawer items above.
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
    item: FeatureDrawerItem,
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
        if (item.icon != null) {
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
