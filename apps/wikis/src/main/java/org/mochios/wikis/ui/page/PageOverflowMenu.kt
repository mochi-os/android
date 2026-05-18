package org.mochios.wikis.ui.page

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.wikis.R
import org.mochios.wikis.model.WikiPermissions

/**
 * Overflow menu rendered next to the wiki page's title.
 *
 * Mirrors the web overflow in
 * `apps/wikis/web/src/features/wiki/wiki-page-content.tsx` lines 228-338 with
 * two labelled sections — **Page** (gated by [permissions.edit] / [delete])
 * and **Wiki** (always visible, with extra rows gated by [permissions.edit] /
 * [manage] and the unsubscribe gate). The order of rows inside each section
 * matches web exactly so users moving between platforms see the same menu.
 *
 * Web uses a nested `DropdownMenuSub` for the RSS feed sub-menu; Material3
 * doesn't have a nested-DropdownMenu primitive, so this flattens the three
 * RSS choices as three labelled rows ("RSS: Changes", "RSS: Comments",
 * "RSS: Changes and comments"). The icon is repeated on each so the user
 * still gets the visual cue.
 *
 * Each callback fires after the row is tapped; the caller is responsible for
 * collapsing the menu (set `expanded = false`) in its handler.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PageOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    wikiId: String,
    slug: String,
    permissions: WikiPermissions,
    commentCount: Int,
    canUnsubscribe: Boolean,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onHistory: () -> Unit,
    onComments: () -> Unit,
    onDelete: () -> Unit,
    onSearch: () -> Unit,
    onTags: () -> Unit,
    onChanges: () -> Unit,
    onNewPage: () -> Unit,
    onSettings: () -> Unit,
    onShare: () -> Unit,
    onUnsubscribe: () -> Unit,
    onRssCopy: (mode: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // [wikiId] and [slug] are accepted on the contract so callers can pass
    // the same set as web's overflow, but every callback is already pre-bound
    // by the host — the menu itself doesn't navigate from these arguments.

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        // -------- Page section --------
        SectionLabel(stringResource(R.string.wikis_page_action_section_page))

        if (permissions.edit) {
            MenuRow(
                icon = Icons.Default.Edit,
                label = stringResource(R.string.wikis_page_action_edit),
                onClick = onEdit,
            )
            MenuRow(
                icon = Icons.Default.DriveFileRenameOutline,
                label = stringResource(R.string.wikis_page_action_rename),
                onClick = onRename,
            )
        }
        MenuRow(
            icon = Icons.Default.History,
            label = stringResource(R.string.wikis_page_action_history),
            onClick = onHistory,
        )
        MenuRow(
            icon = Icons.Default.ModeComment,
            label = pluralStringResource(
                id = R.plurals.wikis_page_action_comments,
                count = commentCount,
                commentCount,
            ),
            onClick = onComments,
        )
        if (permissions.delete) {
            MenuRow(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.wikis_page_action_delete),
                onClick = onDelete,
            )
        }

        HorizontalDivider()

        // -------- Wiki section --------
        SectionLabel(stringResource(R.string.wikis_page_action_section_wiki))

        MenuRow(
            icon = Icons.Default.Search,
            label = stringResource(R.string.wikis_page_action_search),
            onClick = onSearch,
        )
        MenuRow(
            icon = Icons.Default.LocalOffer,
            label = stringResource(R.string.wikis_page_action_tags),
            onClick = onTags,
        )
        MenuRow(
            icon = Icons.Default.History,
            label = stringResource(R.string.wikis_page_action_recent_changes),
            onClick = onChanges,
        )

        // Flattened RSS sub-menu (Material3 doesn't have nested DropdownMenus).
        MenuRow(
            icon = Icons.Default.RssFeed,
            label = stringResource(R.string.wikis_page_action_rss_changes),
            onClick = { onRssCopy("changes") },
        )
        MenuRow(
            icon = Icons.Default.RssFeed,
            label = stringResource(R.string.wikis_page_action_rss_comments),
            onClick = { onRssCopy("comments") },
        )
        MenuRow(
            icon = Icons.Default.RssFeed,
            label = stringResource(R.string.wikis_page_action_rss_all),
            onClick = { onRssCopy("all") },
        )

        if (permissions.edit) {
            MenuRow(
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                label = stringResource(R.string.wikis_page_action_new_page),
                onClick = onNewPage,
            )
        }
        if (permissions.manage) {
            MenuRow(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.wikis_page_action_settings),
                onClick = onSettings,
            )
        }

        MenuRow(
            icon = Icons.Default.Share,
            label = stringResource(R.string.wikis_page_action_share),
            onClick = onShare,
        )

        if (canUnsubscribe) {
            HorizontalDivider()
            MenuRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.wikis_page_action_unsubscribe),
                onClick = onUnsubscribe,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
        onClick = onClick,
    )
}
