// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The edit screen loads the post's attachments after it opens, so a save made
 * before they arrive must leave them alone rather than send an empty order,
 * which the server reads as removing every attachment.
 */
class EditOrderTest {

    @Test
    fun `kept attachments keep their order and new files follow as placeholders`() {
        assertEquals(
            listOf("a", "c", "new:0", "new:1"),
            editOrder(true, listOf("a", "b", "c"), setOf("b"), 2),
        )
    }

    @Test
    fun `removing every attachment sends an empty order`() {
        assertEquals(emptyList<String>(), editOrder(true, listOf("a", "b"), setOf("a", "b"), 0))
    }

    @Test
    fun `before the post has loaded no order is sent`() {
        assertNull(editOrder(false, emptyList(), emptySet(), 0))
        assertNull(editOrder(false, emptyList(), emptySet(), 1))
    }
}
