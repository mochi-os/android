// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.crm.CrmServer
import org.mochios.crm.ui.crmlist.CrmListViewModel
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The drawer lists every CRM, whatever the All CRMs search holds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class DrawerTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("-/list", """{"crms": [{"id": "c1", "fingerprint": "fp1", "name": "Sales"}, {"id": "c2", "fingerprint": "fp2", "name": "Support"}]}""")
    }

    @After
    fun end() = server.stop()

    private fun offered(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a search on All CRMs leaves the drawer listing them all`() {
        val model = CrmListViewModel(server.repository())
        until("the list") { model.uiState.value.crm.size == 2 }
        model.updateSearchQuery("Sal")
        rule.setContent {
            CrmScreen(
                crmId = "",
                onSelectCrm = {},
                onSelectAll = {},
                onFindCrms = {},
                onCreateCrm = {},
                onSettings = {},
                onDesign = {},
                onAddColumn = { _, _, _ -> },
                onOpenNotifications = {},
                listViewModel = model,
            )
        }
        rule.waitForIdle()
        assertTrue(offered("Sales"))
        assertTrue("THE POINT: a CRM the search leaves out", offered("Support"))
    }
}
