// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardPagingTest {
    // The Comptroller computes offset = (page - 1) * limit, so the row-count
    // cursor the tabs keep must reach it as a 1-based page number. Sending the
    // cursor itself asked for page 20 (rows 380+) on the first load-more.
    @Test
    fun rowCountCursorBecomesAOneBasedPage() {
        assertEquals(1, pageFor(0))
        assertEquals(2, pageFor(20))
        assertEquals(3, pageFor(40))
        assertEquals(2, pageFor(25, size = 20))
    }
}
