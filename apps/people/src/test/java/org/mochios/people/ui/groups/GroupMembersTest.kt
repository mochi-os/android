// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.groups

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.people.model.GroupMember
import org.mochios.people.model.GroupMemberType

/**
 * A group's member list names every member: a person the server cannot look
 * up and a nested group since deleted arrive with an empty name and are
 * labelled, never shown by their raw id; the member search reads as searching
 * from the first keystroke.
 */
class GroupMembersTest {

    private fun label(member: GroupMember) = memberLabel(member, "Unknown person", "Deleted group")

    @Test
    fun `a member arrives with its fingerprint`() {
        val member = Gson().fromJson(
            """{"member": "p1", "name": "", "type": "user", "fingerprint": "abcdefghi"}""",
            GroupMember::class.java,
        )
        assertEquals("abcdefghi", member.fingerprint)
    }

    @Test
    fun `a named member shows its name`() {
        assertEquals("Ada", label(GroupMember(member = "p1", name = "Ada")))
    }

    @Test
    fun `a person the server cannot name is labelled, not shown by its id`() {
        assertEquals("Unknown person", label(GroupMember(member = "x".repeat(50), name = "")))
    }

    @Test
    fun `a nested group since deleted is labelled, not shown by its id`() {
        val member = GroupMember(member = "019fadb5", name = "", type = GroupMemberType.GROUP)
        assertEquals("Deleted group", label(member))
    }

    @Test
    fun `typing a query searches from the first keystroke`() {
        val state = AddMemberViewModel.UiState().typed("ad")
        assertTrue(state.searchLoading)
        assertEquals("ad", state.searchQuery)
    }

    @Test
    fun `clearing the query stops searching`() {
        val state = AddMemberViewModel.UiState(searchLoading = true).typed("  ")
        assertFalse(state.searchLoading)
    }
}
