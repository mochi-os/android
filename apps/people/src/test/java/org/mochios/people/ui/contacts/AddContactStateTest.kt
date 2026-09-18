// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.people.model.RelationshipStatus
import org.mochios.people.model.User

/**
 * What the add-contact screen offers for one directory hit: the server's
 * relationship and the contact id it now returns, plus whatever this visit has
 * already done to the row.
 */
class AddContactStateTest {

    private fun user(
        relationship: RelationshipStatus = RelationshipStatus.NONE,
        contact: String = "",
    ) = User(id = "p1", name = "Ada", relationship = relationship, contact = contact)

    @Test
    fun `a stranger may be added or invited`() {
        assertEquals(AddContactState.NONE, addContactState(user()))
    }

    @Test
    fun `a person already in contacts is not offered again`() {
        assertEquals(AddContactState.IN_CONTACTS, addContactState(user(contact = "c1")))
    }

    @Test
    fun `a friend is terminal, whatever the contact says`() {
        assertEquals(
            AddContactState.FRIEND,
            addContactState(user(RelationshipStatus.FRIEND, contact = "c1")),
        )
    }

    @Test
    fun `an invitation waiting from them offers accept`() {
        assertEquals(AddContactState.PENDING, addContactState(user(RelationshipStatus.PENDING)))
    }

    @Test
    fun `an invitation already sent reads as invited`() {
        assertEquals(AddContactState.INVITED, addContactState(user(RelationshipStatus.INVITED)))
    }

    @Test
    fun `the viewer's own row is never actionable`() {
        assertEquals(
            AddContactState.SELF,
            addContactState(user(RelationshipStatus.SELF, contact = "c1")),
        )
    }

    @Test
    fun `an action taken this visit shows without a fresh search`() {
        assertEquals(AddContactState.IN_CONTACTS, addContactState(user(), added = true))
        assertEquals(AddContactState.INVITED, addContactState(user(), invited = true))
        assertEquals(
            AddContactState.FRIEND,
            addContactState(user(RelationshipStatus.PENDING), friended = true),
        )
    }
}
