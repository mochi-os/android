// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.ui.calendar.Searched
import org.mochios.calendars.ui.calendar.searched

/** The list view's search: matches, a wait while later pages may match, or "No matches" once none are left. */
class SearchedTest {

    @Test
    fun `no search shows the list`() {
        assertEquals(Searched.MATCHES, searched("", 0, paging = false, later = true))
    }

    @Test
    fun `a search with matches shows them`() {
        assertEquals(Searched.MATCHES, searched("dentist", 2, paging = false, later = true))
    }

    @Test
    fun `a search with no matches yet waits while later pages may match`() {
        assertEquals(Searched.LOADING, searched("dentist", 0, paging = false, later = true))
        assertEquals(Searched.LOADING, searched("dentist", 0, paging = true, later = false))
    }

    @Test
    fun `a search that matched nothing in everything there is says so`() {
        assertEquals(Searched.NONE, searched("dentist", 0, paging = false, later = false))
    }
}
