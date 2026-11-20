// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crmlist

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.crm.CrmServer
import org.mochios.crm.R
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The CRM list says when an unsubscribe or a pull fails over it, and a new
 * CRM starts private, its switch saying what public grants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrmListTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private val failures = mutableListOf<MochiError>()
    private val jobs = mutableListOf<Job>()

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("-/list", """{"crms": [{"id": "c1", "fingerprint": "fp1", "name": "Sales"}, {"id": "c2", "fingerprint": "fp2", "name": "Support"}]}""")
        server.data("-/create", """{"id": "c9", "fingerprint": "fp9"}""")
    }

    @After
    fun end() {
        jobs.forEach { job -> job.cancel() }
        server.stop()
    }

    private fun list(): CrmListViewModel {
        val model = CrmListViewModel(server.repository())
        jobs += CoroutineScope(Dispatchers.Main).launch { model.actionFailed.collect { failures += it } }
        until("the list") { model.uiState.value.crm.size == 2 }
        return model
    }

    @Test
    fun `a refused unsubscribe is said, and the row stays`() {
        val model = list()
        server.refuse("-/unsubscribe", "Owner cannot unsubscribe")
        model.unsubscribe("c2")
        until("the failure") { failures.isNotEmpty() }
        assertEquals("Owner cannot unsubscribe", failures.single().message)
        assertEquals(2, model.uiState.value.crm.size)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `a pull that fails over the list is said`() {
        val model = list()
        server.refuse("-/list", "Server busy", 503)
        model.refresh()
        until("the failure") { failures.isNotEmpty() }
        assertNull(model.uiState.value.error)
        assertEquals(2, model.uiState.value.crm.size)
    }

    private fun create(public: Boolean): String {
        rule.setContent { CreateCrmScreen(onBack = {}, onCreated = {}, viewModel = CreateCrmViewModel(server.repository())) }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText(context.getString(R.string.crm_create_public)).fetchSemanticsNodes().isNotEmpty())
        rule.onNode(isToggleable()).assertIsOff()
        if (public) rule.onNode(isToggleable()).performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("Leads")
        rule.onNodeWithText(context.getString(R.string.crm_create_action)).performClick()
        until("the create") { server.of("-/create").isNotEmpty() }
        return server.of("-/create").single().body
    }

    @Test
    fun `a new CRM is private unless the switch is turned on`() {
        val body = create(public = false)
        assertTrue(body, body.contains("privacy=private"))
    }

    @Test
    fun `the switch turned on makes it public`() {
        val body = create(public = true)
        assertTrue(body, body.contains("privacy=public"))
    }
}
