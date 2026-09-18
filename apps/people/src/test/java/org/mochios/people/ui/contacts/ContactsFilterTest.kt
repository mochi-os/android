// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.people.model.Contact

/** The contacts list's own search box and its name/recent order. */
class ContactsFilterTest {

    private val contacts = listOf(
        Contact(id = "1", name = "Ada Lovelace", created = 100),
        Contact(id = "2", name = "bob", created = 300),
        Contact(id = "3", name = "Émile Borel", created = 200),
        // The user's label and the directory's disagree: searching either finds
        // the row, because both are shown on it.
        Contact(id = "4", name = "Dad", directory = "Charles Babbage", created = 400),
    )

    @Test
    fun `name order is case and accent insensitive`() {
        assertEquals(
            listOf("Ada Lovelace", "bob", "Dad", "Émile Borel"),
            filterContacts(contacts, "", ContactSortBy.NAME).map { it.name },
        )
    }

    @Test
    fun `recent order is newest first`() {
        assertEquals(
            listOf("4", "2", "3", "1"),
            filterContacts(contacts, "", ContactSortBy.RECENT).map { it.id },
        )
    }

    @Test
    fun `a search matches the label or the directory name`() {
        assertEquals(
            listOf("Ada Lovelace"),
            filterContacts(contacts, "ada", ContactSortBy.NAME).map { it.name },
        )
        assertEquals(
            listOf("Dad"),
            filterContacts(contacts, "babbage", ContactSortBy.NAME).map { it.name },
        )
    }

    @Test
    fun `whitespace alone is not a search`() {
        assertEquals(4, filterContacts(contacts, "   ", ContactSortBy.NAME).size)
    }

    @Test
    fun `a search that matches nothing returns nothing`() {
        assertEquals(0, filterContacts(contacts, "zzz", ContactSortBy.NAME).size)
    }

    @Test
    fun `numbers in a name sort as numbers`() {
        val numbered = listOf(
            Contact(id = "a", name = "Team 10"),
            Contact(id = "b", name = "Team 2"),
        )
        assertEquals(
            listOf("Team 2", "Team 10"),
            filterContacts(numbered, "", ContactSortBy.NAME).map { it.name },
        )
    }
}
