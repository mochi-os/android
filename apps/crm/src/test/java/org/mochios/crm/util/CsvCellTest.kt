// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvCellTest {
    @Test
    fun `a plain value passes through`() {
        assertEquals("Acme Ltd", csvCell("Acme Ltd"))
    }

    @Test
    fun `a comma or quote is quoted`() {
        assertEquals("\"Acme, Ltd\"", csvCell("Acme, Ltd"))
        assertEquals("\"say \"\"hi\"\"\"", csvCell("say \"hi\""))
    }

    @Test
    fun `a formula-leading value is defused with an apostrophe`() {
        assertEquals("'=SUM(A1)", csvCell("=SUM(A1)"))
        assertEquals("'+1", csvCell("+1"))
        assertEquals("'-1", csvCell("-1"))
        assertEquals("'@SUM(A1)", csvCell("@SUM(A1)"))
        assertEquals("'\tx", csvCell("\tx"))
    }

    @Test
    fun `a defused value that also needs quoting keeps the apostrophe inside the quotes`() {
        assertEquals("\"'=1,2\"", csvCell("=1,2"))
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", csvCell("=HYPERLINK(\"http://x\")"))
    }
}
