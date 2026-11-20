// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A view setting the user clears reaches the draft as "", never null: an
 * update sends the draft as JSON, where "" clears the setting on the server
 * and a null is left out, so the old setting stayed.
 */
class ViewListTabTest {

    private val saved = ViewListItem(
        id = "v1",
        name = "Board",
        viewtype = "board",
        filter = "status:open",
        columns = "status",
        rows = "owner",
        fields = "",
        sort = "priority",
        direction = "desc",
        classes = listOf("task"),
        rank = 1,
        border = "priority",
    )

    @Test
    fun `every cleared setting reaches the draft as an empty string`() {
        val form = ViewFormState(saved)
        form.columns = ""
        form.rows = ""
        form.filter = ""
        form.sort = ""
        form.classes = emptySet()
        form.border = " "
        val draft = form.toDraft()
        assertEquals("", draft.columns)
        assertEquals("", draft.rows)
        assertEquals("", draft.filter)
        assertEquals("", draft.sort)
        assertEquals("", draft.classes)
        assertEquals("", draft.border)
    }

    @Test
    fun `kept settings pass through unchanged`() {
        val draft = ViewFormState(saved).toDraft()
        assertEquals("status", draft.columns)
        assertEquals("owner", draft.rows)
        assertEquals("status:open", draft.filter)
        assertEquals("priority", draft.sort)
        assertEquals("desc", draft.direction)
        assertEquals("task", draft.classes)
        assertEquals("priority", draft.border)
    }
}
