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
 * Universal entry for one row of a [MochiListDrawer].
 *
 * Every app adapts whatever it lists — an entity (feed, chat, forum,
 * project), a fixed console destination (market's Purchases, staff's
 * Accounts), or a game — into this shape, so a single composable renders
 * every app's drawer. Keeps icon + unread badge + secondary subtitle as
 * common columns.
 *
 * Leading slot resolution, first match wins:
 *   - [avatarUrl] set        → async avatar image, initials on load failure.
 *   - [seed], no [icon]      → seeded initials circle, for a person-shaped
 *                              row whose avatar path can't be built.
 *   - [seed] and [icon]      → colour-seeded [EntityIconCircle].
 *   - [icon] alone           → the plain icon.
 *   - none of the above      → no leading slot; the title starts the row.
 *
 * @property avatarUrl optional avatar asset path (e.g. "/people/<id>/-/avatar");
 *   rendered as a circular image, falling back to initials on load failure.
 * @property seed stable id used to colour-seed the leading circle so the entity
 *   reads the same here as in its list row / top bar.
 * @property section optional group heading. Consecutive items sharing a
 *   section render under one heading; the heading is drawn when the section
 *   changes from the previous item. Leave null (the default) for a flat list
 *   — that is what the entity-list features (chat, feeds, forums, projects)
 *   want, since their rows are all one kind of thing. The console features
 *   (market, staff) set it to group fixed destinations under Browse /
 *   Buying / Management / ... headings.
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
 * Slide-in left drawer for the per-feature item list (chats, feeds, forums,
 * projects). Matches the mobile-web pattern of hiding the list behind a
 * hamburger so the cold-start path lands directly on the last-viewed
 * detail screen.
 *
 * Drawer layout (top → bottom):
 *   - Header slot (optional: feature title, account picker, etc.), with the
 *     optional "All" pinned item beneath it when [allItem] is non-null. The
 *     aggregate row belongs with the headline rather than at the head of the
 *     list: it stays put while the list scrolls, and the divider below it
 *     separates "everything" from the individual entries.
 *   - Scrollable [items] list, divider above it. Items carrying a
 *     [DrawerItem.section] are grouped under that heading.
 *   - Bottom [actions] slot for feature-level actions (Find, Add, Logout,
 *     Settings, RSS export, ...). Stays visible regardless of scroll.
 *
 * [emptyState] replaces the list when [items] is empty — for features that
 * would rather say "no games yet" than show a blank panel. Features that
 * leave it null keep the blank panel.
 *
 * The caller owns the [drawerState] so the host screen can also wire the
 * hamburger button in its TopAppBar:
 *
 *   val drawerState = rememberDrawerState(DrawerValue.Closed)
 *   val scope = rememberCoroutineScope()
 *   IconButton(onClick = { scope.launch { drawerState.open() } }) { ... }
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
 * Compact action row for the drawer's bottom [MochiListDrawer.actions] slot
 * (Find, Create, Logout, ...). Unlike Material's [androidx.compose.material3.ListItem]
 * it carries no enforced min-height or wide content padding, so the actions sit
 * tight together and align with the drawer items above.
 */
/**
 * The drawer's headline: the app's own name above the item list, in the
 * Material drawer-headline style. Every list app passes its title here so
 * the drawer says where you are — without it the first item sat flush
 * against the top of the screen.
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
 * Group heading inside the drawer's item list, drawn when a run of
 * [DrawerItem.section] values changes. Quieter than [DrawerTitle] —
 * the app-title headline stays the loudest thing in the drawer — and indented to
 * the same 24dp as the rows it introduces.
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
