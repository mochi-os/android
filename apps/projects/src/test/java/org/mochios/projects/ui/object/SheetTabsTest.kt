// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.model.Comment

class SheetTabsTest {

    @Test
    fun `a class taking merge requests has their tab before activity, as on the web`() {
        assertEquals(listOf(TAB_PROPERTIES, TAB_COMMENTS, TAB_REQUESTS, TAB_ACTIVITY), sheetTabs(merge = true))
    }

    @Test
    fun `a class taking none has no merge requests tab`() {
        assertEquals(listOf(TAB_PROPERTIES, TAB_COMMENTS, TAB_ACTIVITY), sheetTabs(merge = false))
    }

    @Test
    fun `the comments count takes in replies, as the web's does`() {
        val thread = listOf(
            Comment(id = "a", children = listOf(Comment(id = "b", children = listOf(Comment(id = "c"))))),
            Comment(id = "d"),
        )
        assertEquals(4, commentCount(thread))
        assertEquals(0, commentCount(emptyList()))
    }
}
