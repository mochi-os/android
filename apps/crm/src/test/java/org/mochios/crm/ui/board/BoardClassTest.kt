// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.crm.model.Crm
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.FieldOption

class BoardClassTest {

    /**
     * A pipeline over two classes with their own stages. The Deal class ranks
     * first, and the options arrive keyed alphabetically, Contact before Deal,
     * as the server's JSON has them.
     */
    private val details = CrmDetails(
        crm = Crm(id = "sales"),
        classes = listOf(CrmClass(id = "deal", rank = 0), CrmClass(id = "contact", rank = 1)),
        options = linkedMapOf(
            "contact" to mapOf("stage" to options("lead", "customer"), "source" to options("referral", "advert")),
            "deal" to mapOf("stage" to options("open", "won", "lost")),
        ),
    )
    private val board = CrmView(id = "pipeline", columns = "stage", classes = listOf("deal", "contact"))

    private fun options(vararg ids: String) = ids.mapIndexed { rank, id -> FieldOption(id = id, rank = rank) }

    @Test
    fun `a board's columns are its first class's options, not every class's`() {
        assertEquals("deal", boardClass(details, board))
        assertEquals(listOf("open", "won", "lost"), boardOptions(details, board, "stage").map { it.id })
    }

    @Test
    fun `a board whose view lists no classes takes the CRM's first class by rank`() {
        // Handed over out of rank order, so the rank has to be what decides.
        val shuffled = details.copy(classes = details.classes.reversed())
        assertEquals("deal", boardClass(shuffled, CrmView(columns = "stage")))
    }

    @Test
    fun `a board on a field only another class has options for has no columns`() {
        // As on the web, which looks no further than the board class.
        assertEquals(emptyList<FieldOption>(), boardOptions(details, board, "source"))
    }
}
