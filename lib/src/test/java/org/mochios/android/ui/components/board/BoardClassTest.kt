// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardClassTest {

    @Test
    fun `a board's class is the first its view lists`() {
        assertEquals("task", boardClass(listOf("ticket", "task"), listOf("task")))
        assertEquals("ticket", boardClass(listOf("ticket", "task"), listOf("ticket", "task")))
    }

    @Test
    fun `a view listing no classes takes the first class there is`() {
        assertEquals("ticket", boardClass(listOf("ticket", "task"), emptyList()))
    }

    @Test
    fun `a view leading with a class that is gone takes the first class there is`() {
        // As the web does: it looks no further down the view's list.
        assertEquals("ticket", boardClass(listOf("ticket", "task"), listOf("removed", "task")))
    }

    @Test
    fun `no classes means no board class`() {
        assertNull(boardClass(emptyList(), listOf("ticket")))
    }
}
