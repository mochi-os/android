// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.find

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.crm.CrmServer
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A recommended CRM's card says what the directory's blurb says of it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecommendationTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("-/recommendations", """{"crms": [{"id": "e1", "name": "Sales", "blurb": "Every lead in one place", "fingerprint": "fp1", "server": "https://example.org"}]}""")
    }

    @After
    fun end() = server.stop()

    @Test
    fun `a recommendation's blurb is its description`() {
        val crm = runBlocking { server.repository().getRecommendations() }.single()
        assertEquals("Every lead in one place", crm.description)
        assertEquals("e1", crm.id)
        assertEquals("fp1", crm.fingerprint)
        assertEquals("Sales", crm.name)
        assertEquals("https://example.org", crm.server)
    }
}
