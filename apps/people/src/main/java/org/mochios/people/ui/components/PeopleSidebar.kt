// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.people.R

/**
 * Top-level navigable sections in the People app sidebar. Mirrors the web
 * sidebar (`apps/people/web/src/components/layout/people-layout.tsx`):
 * Friends, Invitations, Groups, Profile.
 */
enum class PeopleSidebarSection { FRIENDS, INVITATIONS, GROUPS, PROFILE }

/**
 * Drawer body for the People app's persistent left rail. Each top-level
 * People screen wraps its body in [androidx.compose.material3.ModalNavigationDrawer]
 * with this composable as the drawer content, so a hamburger menu in the
 * top-bar opens the same navigation choices everywhere.
 *
 * Highlights the row matching [current] so the active section is obvious;
 * tapping a row calls [onSelect] (callers typically close the drawer and
 * navigate). Detail screens (Group / Person view) deliberately don't use the
 * sidebar — their back button stays the only nav affordance.
 */
@Composable
fun PeopleSidebar(
    current: PeopleSidebarSection,
    onSelect: (PeopleSidebarSection) -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
        Column {
            // Header — matches the web sidebar's "People" group title.
            Text(
                text = stringResource(R.string.people_sidebar_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SidebarRow(
                section = PeopleSidebarSection.FRIENDS,
                current = current,
                icon = Icons.Default.People,
                labelRes = R.string.people_friends_title,
                onSelect = onSelect,
            )
            SidebarRow(
                section = PeopleSidebarSection.INVITATIONS,
                current = current,
                icon = Icons.Default.MailOutline,
                labelRes = R.string.people_invitations_title,
                onSelect = onSelect,
            )
            SidebarRow(
                section = PeopleSidebarSection.GROUPS,
                current = current,
                icon = Icons.Default.Groups,
                labelRes = R.string.people_groups_title,
                onSelect = onSelect,
            )
            SidebarRow(
                section = PeopleSidebarSection.PROFILE,
                current = current,
                icon = Icons.Default.AccountCircle,
                labelRes = R.string.people_profile_title,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun SidebarRow(
    section: PeopleSidebarSection,
    current: PeopleSidebarSection,
    icon: ImageVector,
    labelRes: Int,
    onSelect: (PeopleSidebarSection) -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
        selected = current == section,
        onClick = { onSelect(section) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
