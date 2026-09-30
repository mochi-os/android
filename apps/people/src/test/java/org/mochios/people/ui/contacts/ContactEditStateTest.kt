// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.api.MochiError
import org.mochios.people.model.Book

class ContactEditStateTest {

    private val asking = ContactEditUiState(unfriendRequested = true, isToggling = true)

    @Test
    fun `a failed unfriend keeps the confirmation up, saying why`() {
        val failure = MochiError.NetworkError()
        val after = asking.unfriended(failure)
        assertTrue(after.unfriendRequested)
        assertEquals(failure, after.error)
        assertFalse(after.isToggling)
    }

    @Test
    fun `a finished unfriend closes the confirmation`() {
        val after = asking.unfriended(null)
        assertFalse(after.unfriendRequested)
        assertFalse(after.isToggling)
    }

    private val books = listOf(
        Book(id = "b1", name = "Contacts", isDefault = true),
        Book(id = "b2", name = "Work"),
    )

    @Test
    fun `a new contact goes in the book it was started from`() {
        assertEquals("b2", startBook(books, "b2"))
    }

    @Test
    fun `a new contact goes in the default book otherwise`() {
        assertEquals("b1", startBook(books, ""))
        assertEquals("b1", startBook(books, "gone"))
    }
}
