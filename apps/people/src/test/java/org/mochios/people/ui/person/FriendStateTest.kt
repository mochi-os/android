// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.person

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.people.api.ContactsListResponse
import org.mochios.people.model.Contact
import org.mochios.people.model.FriendInvite

/**
 * Where a person stands with the viewer, read off the contacts response the
 * list screen already fetches. A contact who is not a friend is its own state:
 * the profile offers to invite them rather than to add them again.
 */
class FriendStateTest {

    private val contacts = ContactsListResponse(
        contacts = listOf(
            Contact(id = "c1", person = "friend", friend = true, name = "Ada"),
            Contact(id = "c2", person = "contact", friend = false, name = "Bob"),
            Contact(id = "c3", person = "", name = "Offline only"),
        ),
        received = listOf(FriendInvite(id = "asked", direction = "from", name = "Cleo")),
        sent = listOf(FriendInvite(id = "invited", direction = "to", name = "Dev")),
    )

    @Test
    fun `a friend reads as a friend`() {
        assertEquals(FriendState.Friend, friendState(contacts, "friend"))
    }

    @Test
    fun `a contact who is not a friend has its own state`() {
        assertEquals(FriendState.Contact, friendState(contacts, "contact"))
    }

    @Test
    fun `a stranger is not a contact`() {
        assertEquals(FriendState.NotFriend, friendState(contacts, "stranger"))
    }

    @Test
    fun `an invitation received carries the person it answers`() {
        val state = friendState(contacts, "asked")
        assertTrue(state is FriendState.InvitedByThem)
        assertEquals("asked", (state as FriendState.InvitedByThem).person)
    }

    @Test
    fun `an invitation sent reads as invited`() {
        assertEquals(FriendState.InvitedThem, friendState(contacts, "invited"))
    }

    @Test
    fun `the viewer's own identity reads as self`() {
        assertEquals(FriendState.Self, friendState(contacts, "me", me = "me"))
    }

    @Test
    fun `a contact with no person is never matched`() {
        // A plain address-book entry has no entity id, so an empty person must
        // not collide with it.
        assertEquals(FriendState.NotFriend, friendState(contacts, ""))
    }
}
