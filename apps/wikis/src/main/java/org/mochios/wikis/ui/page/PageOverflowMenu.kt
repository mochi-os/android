// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuDivider
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiDropdownSubmenu
import org.mochios.wikis.R
import org.mochios.wikis.model.WikiPermissions

/**
 * Material3 has no nested-DropdownMenu, so web's RSS fly-out becomes one row
 * that expands its three modes in place — the same shape the wiki list's
 * overflow uses. Callbacks fire on tap; the caller collapses the menu itself.
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

    // Held outside the menu content, which is disposed on collapse, so the
    // sub-menu is closed again the next time the overflow opens.
    var rssExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) rssExpanded = false }

    MochiDropdownMenu(
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

        MochiDropdownMenuDivider()

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

        // The RSS modes live behind one row rather than three of their own.
        MochiDropdownSubmenu(
            text = { Text(stringResource(R.string.wikis_rss_menu)) },
            expanded = rssExpanded,
            onExpandedChange = { rssExpanded = it },
            leadingIcon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
        ) {
            RssModes(onSelect = onRssCopy)
        }

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
            MochiDropdownMenuDivider()
            MenuRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.wikis_page_action_unsubscribe),
                onClick = onUnsubscribe,
            )
        }
    }
}

/** The three modes web offers, as the fly-out's own rows. */
@Composable
private fun RssModes(onSelect: (String) -> Unit) {
    MochiDropdownMenuItem(
        text = { Text(stringResource(R.string.wikis_rss_changes)) },
        onClick = { onSelect("changes") },
    )
    MochiDropdownMenuItem(
        text = { Text(stringResource(R.string.wikis_rss_comments)) },
        onClick = { onSelect("comments") },
    )
    MochiDropdownMenuItem(
        text = { Text(stringResource(R.string.wikis_rss_both)) },
        onClick = { onSelect("all") },
    )
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
    MochiDropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}
