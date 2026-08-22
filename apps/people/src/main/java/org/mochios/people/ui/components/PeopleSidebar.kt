// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.people.R

enum class PeopleSidebarSection { FRIENDS, INVITATIONS, GROUPS, PROFILE }

@Composable
fun peopleDrawerItems(): List<DrawerItem> = listOf(
    DrawerItem(
        id = PeopleSidebarSection.FRIENDS.name,
        title = stringResource(R.string.people_friends_title),
        icon = Icons.Default.People,
    ),
    DrawerItem(
        id = PeopleSidebarSection.INVITATIONS.name,
        title = stringResource(R.string.people_invitations_title),
        icon = Icons.Default.MailOutline,
    ),
    DrawerItem(
        id = PeopleSidebarSection.GROUPS.name,
        title = stringResource(R.string.people_groups_title),
        icon = Icons.Default.Groups,
    ),
    DrawerItem(
        id = PeopleSidebarSection.PROFILE.name,
        title = stringResource(R.string.people_profile_title),
        icon = Icons.Default.AccountCircle,
    ),
)

/** Resolves a [peopleDrawerItems] row id back to its section. */
fun peopleDrawerSection(itemId: String): PeopleSidebarSection =
    PeopleSidebarSection.valueOf(itemId)
