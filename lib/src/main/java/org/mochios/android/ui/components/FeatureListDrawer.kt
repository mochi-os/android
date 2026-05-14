package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
                                )
                            }
                            item("__divider__") {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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

@Composable
private fun DrawerItemRow(
    item: FeatureDrawerItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
        if (item.unread > 0) {
            Badge {
                Text(text = item.unread.toString())
            }
        }
    }
}
