// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.format.formatFingerprint
import org.mochios.android.i18n.DateFormat
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.ui.components.FieldDisplay
import org.mochios.android.util.fingerprint
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.Person
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.crm.ui.tree.TreeView
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A card and a tree row show each value as the web's board card does, by its
 * field's type: a date as the stored day in the user's format, west of UTC and
 * before 1970 too, overdue only once its day is past; a number in the user's
 * format; a ticked box by its field's name and an unticked one not at all; a
 * checklist as its progress; and a person the CRM has no name for by their
 * fingerprint, never their id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h1600dp")
class FieldValuesTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket

    private val ada = "12C83B6Phg9Ap8bK58uqrzWKBYRXAo9pSzsHzSJnEsAnCnRpdob"
    private val tasks = """[{\"id\":\"a\",\"text\":\"Call\",\"done\":true},{\"id\":\"b\",\"text\":\"Mail\",\"done\":false}]"""

    private val format = Format(
        UserPreferences(
            dateFormat = DateFormat.DD_SLASH_MM_YYYY,
            numberFormat = NumberFormat.EUROPEAN_DOT_COMMA,
            timezone = "America/Los_Angeles",
        )
    )

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/people", """{"people": [{"id": "bob", "name": "Bob"}]}""")
        socket = server.socket()
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.stop()
    }

    private fun model(viewtype: String): CrmViewModel {
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}],
            "fields": {"company": [
                {"id": "name", "name": "Name", "fieldtype": "text", "card": 1},
                {"id": "stage", "name": "Stage", "fieldtype": "enumerated", "card": 1},
                {"id": "owner", "name": "Owner", "fieldtype": "user", "card": 1},
                {"id": "due", "name": "Due", "fieldtype": "date", "card": 1},
                {"id": "amount", "name": "Amount", "fieldtype": "number", "card": 1},
                {"id": "active", "name": "Active", "fieldtype": "checkbox", "card": 1},
                {"id": "closed", "name": "Closed", "fieldtype": "checkbox", "card": 1},
                {"id": "tasks", "name": "Tasks", "fieldtype": "checklist", "card": 1}]},
            "options": {"company": {"stage": [{"id": "lead", "name": "Lead"}]}},
            "views": [{"id": "v1", "name": "View", "viewtype": "$viewtype", "columns": "stage", "classes": ["company"]}],
            "hierarchy": {"company": [""]}}""")
        server.data("c1/-/objects", """{"objects": [
            {"id": "a", "class": "company", "parent": "", "rank": "a0", "values": {"name": "Acme", "stage": "lead",
                "owner": "$ada", "due": "1965-03-02", "amount": "1234.5", "active": "1", "closed": "0", "tasks": "$tasks"}},
            {"id": "b", "class": "company", "parent": "", "rank": "a1", "values": {"name": "Globex", "stage": "lead", "owner": "bob"}}
            ], "watched": []}""")
        val model = CrmViewModel(
            SavedStateHandle(mapOf("crmId" to "c1")),
            server.repository(),
            socket,
            server.session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        until("the CRM") {
            model.uiState.value.crmDetails != null && model.uiState.value.objects.size == 2 &&
                model.uiState.value.people.isNotEmpty()
        }
        return model
    }

    private fun shown(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertValues() {
        assertTrue("the day stored, in the user's format", shown("02/03/1965"))
        assertTrue("the amount in the user's format", shown("1.234,5"))
        assertTrue("a ticked box by its field's name", shown("Active"))
        assertFalse("an unticked box not at all", shown("Closed"))
        assertTrue("a checklist's progress", shown("1/2"))
        assertFalse("never a checklist's JSON", shown(tasks.replace("\\\"", "\"")))
        assertTrue("an unnamed person by fingerprint", shown(formatFingerprint(fingerprint(ada))))
        assertFalse("never a person's id", shown(ada))
        assertTrue("a named person by name", shown("Bob"))
        assertFalse(shown("1965-03-02"))
        assertFalse(shown("1234.5"))
    }

    @Test
    fun `a tree row shows each value by its field's type`() {
        val model = model("list")
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                val state by model.uiState.collectAsState()
                TreeView(
                    objects = state.objects,
                    view = model.getActiveView(),
                    viewModel = model,
                    crmDetails = state.crmDetails,
                    people = state.people,
                    allObjects = state.objects,
                    onObjectClick = {},
                )
            }
        }
        rule.waitForIdle()
        assertValues()
    }

    @Test
    fun `a board card shows each value by its field's type`() {
        val model = model("board")
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                val state by model.uiState.collectAsState()
                BoardView(
                    objects = state.objects,
                    view = model.getActiveView(),
                    viewModel = model,
                    crmDetails = state.crmDetails,
                    people = state.people,
                    onObjectClick = {},
                )
            }
        }
        rule.waitForIdle()
        assertValues()
    }

    @Test
    fun `a date is overdue once its day is past in the user's zone, not on the day itself`() {
        val model = model("list")
        val due = CrmField(id = "due", name = "Due", fieldtype = "date")
        val today = format.today()
        fun date(day: String) = fieldShown(
            due,
            CrmObject(id = "x", objectClass = "company", values = mapOf("due" to day)),
            model,
            emptyList<Person>(),
            format,
        ) as FieldDisplay.Date
        assertFalse("today is not overdue", date(today.toString()).overdue)
        assertTrue("yesterday is", date(today.minusDays(1).toString()).overdue)
        assertTrue("before 1970 too", date("1965-03-02").overdue)
        assertEquals("02/03/1965", date("1965-03-02").text)
    }
}
