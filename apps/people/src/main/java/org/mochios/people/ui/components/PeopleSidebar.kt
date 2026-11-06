// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.android.ui.components.personAvatarPath
import org.mochios.android.util.NaturalCompare
import org.mochios.people.R
import org.mochios.people.model.Book
import org.mochios.people.model.Group

enum class PeopleSidebarSection { CONTACTS, INVITATIONS, GROUPS, PROFILE }

/** Marks a drawer row as an address book rather than a section. */
private const val BOOK_PREFIX = "book:"

/** Marks a drawer row as a group rather than a section. */
private const val GROUP_PREFIX = "group:"

/**
 * The drawer's rows: one per address book and Invitations, then the groups
 * under their own heading. [peopleProfileItem] and [peopleAllContactsItem]
 * are pinned above them.
 */
@Composable
fun peopleDrawerItems(
    books: List<Book> = emptyList(),
    groups: List<Group> = emptyList(),
): List<DrawerItem> {
    val groupsHeading = stringResource(R.string.people_groups_title)
    return books.sortedWith(compareBy(NaturalCompare) { it.name }).map { book ->
        DrawerItem(
            id = BOOK_PREFIX + book.id,
            title = book.name,
            icon = Icons.Outlined.Contacts,
        )
    } + listOf(
        DrawerItem(
            id = PeopleSidebarSection.INVITATIONS.name,
            title = stringResource(R.string.people_invitations_title),
            icon = Icons.Outlined.MailOutline,
        ),
    ) + groups.sortedWith(compareBy(NaturalCompare) { group -> group.name }).map { group ->
        DrawerItem(
            id = GROUP_PREFIX + group.id,
            title = group.name,
            icon = Icons.Outlined.Groups,
            section = groupsHeading,
        )
    }
}

/**
 * The Profile row pinned at the top, showing the user's avatar when their
 * [identity] is known and the profile icon until then.
 */
@Composable
fun peopleProfileItem(identity: String?): DrawerItem = DrawerItem(
    id = PeopleSidebarSection.PROFILE.name,
    title = stringResource(R.string.people_profile_title),
    icon = Icons.Outlined.AccountCircle,
    avatarUrl = personAvatarPath(identity),
    seed = identity,
)

/** The aggregate row above the books, holding every contact. */
@Composable
fun peopleAllContactsItem(): DrawerItem = DrawerItem(
    id = PeopleSidebarSection.CONTACTS.name,
    title = stringResource(R.string.people_contacts_all),
    icon = Icons.Outlined.Contacts,
)

/** The drawer row id of an address book. */
fun peopleBookItemId(book: String): String = BOOK_PREFIX + book

/** Resolves a drawer row id to its section, or null for an address book or a group. */
fun peopleDrawerSection(itemId: String): PeopleSidebarSection? =
    if (itemId.startsWith(BOOK_PREFIX) || itemId.startsWith(GROUP_PREFIX)) {
        null
    } else {
        PeopleSidebarSection.entries.firstOrNull { it.name == itemId }
    }

/** Resolves a drawer row id to its address book, or null for a section. */
fun peopleDrawerBook(itemId: String): String? =
    itemId.removePrefix(BOOK_PREFIX).takeIf { itemId.startsWith(BOOK_PREFIX) }

/** Resolves a drawer row id to its group, or null for anything else. */
fun peopleDrawerGroup(itemId: String): String? =
    itemId.removePrefix(GROUP_PREFIX).takeIf { itemId.startsWith(GROUP_PREFIX) }
