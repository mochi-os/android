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
import org.mochios.android.util.NaturalCompare
import org.mochios.people.R
import org.mochios.people.model.Book

enum class PeopleSidebarSection { CONTACTS, INVITATIONS, GROUPS, PROFILE }

/** Marks a drawer row as an address book rather than a section. */
private const val BOOK_PREFIX = "book:"

/**
 * The drawer's rows: one per address book, then the app's other sections.
 * Contacts itself is the pinned [peopleAllContactsItem] above them.
 */
@Composable
fun peopleDrawerItems(books: List<Book> = emptyList()): List<DrawerItem> =
    books.sortedWith(compareBy(NaturalCompare) { it.name }).map { book ->
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
        DrawerItem(
            id = PeopleSidebarSection.GROUPS.name,
            title = stringResource(R.string.people_groups_title),
            icon = Icons.Outlined.Groups,
        ),
        DrawerItem(
            id = PeopleSidebarSection.PROFILE.name,
            title = stringResource(R.string.people_profile_title),
            icon = Icons.Outlined.AccountCircle,
        ),
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

/** Resolves a drawer row id to its section, or null for an address book. */
fun peopleDrawerSection(itemId: String): PeopleSidebarSection? =
    if (itemId.startsWith(BOOK_PREFIX)) {
        null
    } else {
        PeopleSidebarSection.entries.firstOrNull { it.name == itemId }
    }

/** Resolves a drawer row id to its address book, or null for a section. */
fun peopleDrawerBook(itemId: String): String? =
    itemId.removePrefix(BOOK_PREFIX).takeIf { itemId.startsWith(BOOK_PREFIX) }
