// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.crm.CrmServer
import org.mochios.crm.R
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * A field's editor sends what the server reads: flags always, "" when none is
 * on, an emptied position or pattern as "", a blank rows as the one row the
 * web shows; it shows the type it cannot change, reseeds from what the server
 * stored, and says why a save was refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class FieldDetailTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer

    private val update = "c1/-/classes/company/fields/notes/update"

    /** The field as the server holds it. */
    @Volatile private var field = """{"id": "notes", "name": "Notes", "fieldtype": "text", "flags": "required", "position": "header", "pattern": "^a", "rows": 3}"""

    @Before
    fun begin() {
        server = CrmServer(context)
        server.on("c1/-/information") {
            CrmServer.ok("""{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
                "classes": [{"id": "company", "name": "Company", "title": "name"}],
                "fields": {"company": [$field]}, "options": {}, "views": [], "hierarchy": {"company": [""]}}""")
        }
    }

    @After
    fun end() = server.stop()

    private fun show() {
        val model = FieldDetailViewModel(
            SavedStateHandle(mapOf("crmId" to "c1", "classId" to "company", "fieldId" to "notes")),
            server.repository(),
        )
        rule.setContent { FieldDetailScreen(onBack = {}, onAddOption = {}, onEditOption = {}, viewModel = model) }
        until("the field") { model.uiState.value.crmDetails != null }
        rule.waitForIdle()
    }

    private fun offered(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private val save get() = context.getString(MochiR.string.common_save)

    private fun saved(): com.google.gson.JsonObject {
        rule.onNodeWithText(save).performClick()
        until("the save") { server.of(update).isNotEmpty() }
        return server.of(update).last().json()
    }

    private fun type(label: Int, text: String) {
        rule.onNode(hasSetTextAction() and hasText(context.getString(label))).performTextReplacement(text)
        rule.waitForIdle()
    }

    @Test
    fun `switching off the only flag sends no flags rather than leaving them out`() {
        show()
        assertFalse(offered(save))
        rule.onNodeWithText(context.getString(R.string.crm_field_required)).performClick()
        rule.waitForIdle()
        val body = saved()
        assertEquals("", body.get("flags").asString)
        assertFalse("an unchanged position is left alone", body.has("position"))
        assertFalse("the type is never sent", body.has("fieldtype"))
    }

    @Test
    fun `the default position and an emptied pattern are sent as empty`() {
        show()
        rule.onNodeWithText(context.getString(R.string.crm_field_position_header)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.crm_field_position_default)).performClick()
        rule.waitForIdle()
        type(R.string.crm_field_pattern, "")
        val body = saved()
        assertEquals("", body.get("position").asString)
        assertEquals("", body.get("pattern").asString)
        assertEquals("required", body.get("flags").asString)
    }

    @Test
    fun `blank rows is the single row the web shows`() {
        show()
        type(R.string.crm_field_rows, "")
        assertEquals(1, saved().get("rows").asInt)
    }

    @Test
    fun `after a save the form shows what the server stored`() {
        show()
        type(R.string.crm_field_pattern, "^b ")
        // The server stores the pattern trimmed.
        field = field.replace("\"^a\"", "\"^b\"")
        assertEquals("^b ", saved().get("pattern").asString)
        until("the reload") { !offered(save) }
        assertTrue(offered("^b"))
    }

    @Test
    fun `the type is shown, not offered, and a text field has no options`() {
        show()
        rule.onNodeWithText(context.getString(MochiR.string.field_type_text)).performClick()
        rule.waitForIdle()
        assertFalse(offered(context.getString(MochiR.string.field_type_number)))
        assertFalse(offered(context.getString(R.string.crm_field_options)))
    }

    @Test
    fun `the length limits the server holds open filled in, and one cleared is sent as none`() {
        field = """{"id": "notes", "name": "Notes", "fieldtype": "text", "flags": "", "pattern": "^a", "minlength": 2, "maxlength": 40}"""
        show()
        assertTrue(offered("2"))
        assertTrue(offered("40"))
        assertTrue(offered("^a"))
        type(R.string.crm_field_max_length, "")
        val body = saved()
        assertEquals(0, body.get("maxlength").asInt)
        assertFalse("an unchanged limit is left alone", body.has("minlength"))
    }

    @Test
    fun `a refused save says why over the form`() {
        show()
        server.refuse(update, "Name in use")
        type(R.string.crm_field_name, "Other")
        saved()
        until("the message") { offered("Name in use") }
        assertTrue(offered(context.getString(R.string.crm_field_required)))
    }
}
