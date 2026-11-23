// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiInfoResponse

/** A failed information request is asked again by the next load; one that arrived is not. */
class WikiInfoLoaderTest {

    @Test
    fun `a failed request is asked again and the information is handed over once`() = runBlocking {
        var asked = 0
        val handed = mutableListOf<WikiInfoResponse>()
        val answer = WikiInfoResponse(wiki = WikiInfo(id = "w"))
        val loader = WikiInfoLoader(
            fetch = {
                asked++
                if (asked == 1) throw IOException("offline")
                answer
            },
            loaded = { handed += it },
        )

        try {
            loader.ensure()
            fail("the first request's failure should reach the caller")
        } catch (_: IOException) {
        }
        assertFalse(loader.held)
        assertTrue(handed.isEmpty())

        loader.ensure()
        loader.ensure()

        assertEquals(2, asked)
        assertTrue(loader.held)
        assertEquals(listOf(answer), handed)
    }
}
