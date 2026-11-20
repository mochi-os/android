// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A list read a page at a time asks for what follows the cursor its last page
 * answered, and ends when the server says so; a row listed twice is kept once.
 */
class PagingTest {

    @Test
    fun `the next page is asked for after the cursor the last one answered`() {
        val first = Paging().advance(count = 20, total = 50, next = "c20")
        assertEquals("c20", first.cursor)
        assertEquals(2, first.page)
        assertTrue(first.more)
    }

    @Test
    fun `an empty cursor ends the list whatever the total says`() {
        val last = Paging().advance(count = 20, total = 50, next = "")
        assertFalse(last.more)
        assertNull(last.cursor)
    }

    @Test
    fun `a server that sends no cursor is paged by number until the total is read`() {
        val first = Paging().advance(count = 20, total = 30, next = null)
        assertTrue(first.more)
        assertNull(first.cursor)
        assertEquals(2, first.page)
        assertFalse(first.advance(count = 10, total = 30, next = null).more)
    }

    @Test
    fun `a page with no rows ends the list`() {
        assertFalse(Paging().advance(count = 0, total = 30, next = null).more)
        assertFalse(Paging().advance(count = 0, total = 30, next = "c0").more)
    }

    @Test
    fun `a row a page repeats is listed once`() {
        val held = listOf("a", "b", "c")
        val page = listOf("c", "d", "d", "e")
        assertEquals(listOf("a", "b", "c", "d", "e"), held.append(page) { row -> row })
    }
}
