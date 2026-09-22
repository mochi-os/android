// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.
package org.mochios.people

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.people.model.Contact
import org.mochios.people.model.FriendState
import org.mochios.people.model.friendState

/**
 * The friend switch shows the handshake: a friend, an invitation the other
 * side has not answered, or neither. A pending invitation is known only from
 * the sent list, since the contact row's flag flips on accept.
 */
class ContactFriendStateTest {
    private val sent = setOf("person-b")

    @Test
    fun aFriendIsAFriendWhateverTheSentList() {
        assertEquals(FriendState.FRIEND, Contact(person = "person-b", friend = true).friendState(sent))
        assertEquals(FriendState.FRIEND, Contact(person = "person-a", friend = true).friendState(emptySet()))
    }

    @Test
    fun aLinkedContactWithAnInvitationOutIsInvited() {
        assertEquals(FriendState.INVITED, Contact(person = "person-b", friend = false).friendState(sent))
    }

    @Test
    fun aLinkedContactWithNoInvitationIsNeither() {
        assertEquals(FriendState.NONE, Contact(person = "person-a", friend = false).friendState(sent))
    }

    @Test
    fun aPlainContactIsNeitherEvenIfTheSentListIsNotEmpty() {
        assertEquals(FriendState.NONE, Contact(person = "", friend = false).friendState(sent))
    }
}
