// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The tap order, as the web client's `undoChain` tests have it. */
class UndoTest {

    @Test
    fun `puts back the value from before when the only tap is refused`() {
        val step = UndoChain<Int>().start("a")
        step.remember(1)
        assertEquals(Previous(1), step.refused())
    }

    @Test
    fun `keeps only the first value remembered`() {
        val step = UndoChain<Int>().start("a")
        step.remember(1)
        step.remember(2)
        assertEquals(Previous(1), step.refused())
    }

    @Test
    fun `changes nothing for a refusal while a later tap waits, then hands it the value`() {
        val chain = UndoChain<Int>()
        val first = chain.start("a")
        val second = chain.start("a")
        first.remember(1)
        second.remember(2)
        assertNull(first.refused())
        assertEquals(Previous(1), second.refused())
    }

    @Test
    fun `keeps the later tap its own value when the refused one remembered none`() {
        val chain = UndoChain<Int>()
        val first = chain.start("a")
        val second = chain.start("a")
        second.remember(2)
        assertNull(first.refused())
        assertEquals(Previous(2), second.refused())
    }

    @Test
    fun `undoes nothing for an earlier tap once a later one is taken`() {
        val chain = UndoChain<Int>()
        val first = chain.start("a")
        val second = chain.start("a")
        first.remember(1)
        second.remember(2)
        second.accepted()
        assertNull(first.refused())
    }

    @Test
    fun `goes back to the taken tap when a later one is refused`() {
        val chain = UndoChain<Int>()
        val first = chain.start("a")
        val second = chain.start("a")
        first.remember(1)
        second.remember(2)
        first.accepted()
        assertEquals(Previous(2), second.refused())
    }

    @Test
    fun `keeps each thing on its own`() {
        val chain = UndoChain<Int>()
        val a = chain.start("a")
        val b = chain.start("b")
        a.remember(1)
        b.remember(5)
        b.accepted()
        assertEquals(Previous(1), a.refused())
    }

    @Test
    fun `holds null as a value`() {
        val step = UndoChain<String?>().start("a")
        step.remember(null)
        step.remember("like")
        assertEquals(Previous<String?>(null), step.refused())
    }
}
