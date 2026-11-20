// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.ViewDraft
import org.mochios.crm.CrmServer
import org.mochios.crm.Sent
import org.mochios.crm.ui.settings.CrmSettingsViewModel
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A setting cleared on the phone arrives cleared: a view's lane, border, sort,
 * filter and classes, an option's colour and icon, and a CRM's description go
 * in JSON, where "" is a value the server reads rather than one it skips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClearedSettingsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "description": "Pipeline", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": ""}], "fields": {},
            "options": {"company": {"stage": [{"id": "lead", "name": "Lead", "colour": "#ff0000", "icon": "star"}]}},
            "views": [{"id": "v1", "name": "Board", "viewtype": "board", "columns": "stage", "rows": "owner", "border": "stage", "sort": "created", "classes": ["company"]}],
            "hierarchy": {"company": [""]}}""")
    }

    @After
    fun end() = server.stop()

    private fun sent(path: String): Sent {
        until(path) { server.of(path).isNotEmpty() }
        val sent = server.of(path).single()
        assertTrue(sent.type, sent.type.startsWith("application/json"))
        return sent
    }

    @Test
    fun `a view's settings cleared to none are sent as empty`() {
        val model = EditViewViewModel(SavedStateHandle(mapOf("crmId" to "c1", "viewId" to "v1")), server.repository())
        // Blanks as the shared form hands them over, null or "".
        model.updateView(ViewDraft(name = "Board", viewtype = "board", columns = "stage", rows = null, filter = "", sort = null, direction = "asc", classes = null, border = ""))
        val body = sent("c1/-/views/v1/update").json()
        for (setting in listOf("rows", "border", "sort", "classes", "filter")) {
            assertEquals(setting, "", body.get(setting).asString)
        }
        assertEquals("stage", body.get("columns").asString)
        until("saved") { model.uiState.value.saved }
    }

    @Test
    fun `an option's colour and icon cleared are sent as empty`() {
        val model = OptionViewModel(
            SavedStateHandle(mapOf("crmId" to "c1", "classId" to "company", "fieldId" to "stage", "optionId" to "lead")),
            server.repository(),
        )
        until("the option") { model.uiState.value.option != null }
        model.save("Lead", "", "")
        val body = sent("c1/-/classes/company/fields/stage/options/lead/update").json()
        assertEquals("", body.get("colour").asString)
        assertEquals("", body.get("icon").asString)
        assertEquals("Lead", body.get("name").asString)
    }

    @Test
    fun `a new option with no colour or icon leaves them out`() {
        val model = OptionViewModel(
            SavedStateHandle(mapOf("crmId" to "c1", "classId" to "company", "fieldId" to "stage")),
            server.repository(),
        )
        model.save("Won", "", "")
        until("the create") { server.of("c1/-/classes/company/fields/stage/options/create").isNotEmpty() }
        val body = server.of("c1/-/classes/company/fields/stage/options/create").single().body
        assertEquals("name=Won", body)
    }

    @Test
    fun `a CRM's description emptied is sent as empty`() {
        val model = CrmSettingsViewModel(SavedStateHandle(mapOf("crmId" to "c1")), server.repository())
        until("the CRM") { model.uiState.value.crm != null }
        model.updateDescription("")
        model.saveCrm()
        val body = sent("c1/-/update").json()
        assertEquals("", body.get("description").asString)
        assertEquals("Sales", body.get("name").asString)
        assertFalse(body.has("privacy"))
    }
}
