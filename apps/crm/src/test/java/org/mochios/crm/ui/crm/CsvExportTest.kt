// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The CSV export heads its ID, Class and Parent columns in the user's
 * language, as the web's does, and writes an option or a person by name; a
 * person the CRM has no name for keeps their id, as the ID and Parent columns
 * do, rather than losing who it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CsvExportTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/information", """{"crm": {"id": "c1", "name": "Sales", "access": "owner"},
            "classes": [{"id": "company", "name": "Company", "title": "name"}],
            "fields": {"company": [{"id": "name", "name": "Name", "fieldtype": "text", "rank": 1},
                {"id": "owner", "name": "Owner", "fieldtype": "user", "rank": 2},
                {"id": "stage", "name": "Stage", "fieldtype": "enumerated", "rank": 3}]},
            "options": {"company": {"stage": [{"id": "lead", "name": "Lead"}]}},
            "views": [{"id": "v1", "name": "List", "viewtype": "list", "classes": ["company"]}],
            "hierarchy": {"company": [""]}}""")
        server.data("c1/-/objects", """{"objects": [
            {"id": "a", "class": "company", "parent": "", "rank": "a0", "values": {"name": "Acme", "owner": "bob", "stage": "lead"}},
            {"id": "b", "class": "company", "parent": "", "rank": "a1", "values": {"name": "Globex", "owner": "zed"}},
            {"id": "c", "class": "company", "parent": "", "rank": "a2", "values": {"name": "Initech", "owner": "nameless"}}
            ], "watched": []}""")
        server.data("c1/-/people", """{"people": [{"id": "bob", "name": "Bob"}, {"id": "nameless", "name": ""}]}""")
        socket = server.socket()
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.stop()
    }

    private fun csv(): List<String> {
        val model = CrmViewModel(
            SavedStateHandle(mapOf("crmId" to "c1")),
            server.repository(),
            socket,
            server.session,
            ActiveViewStore(context),
            CollapsedColumnsStore(context),
        )
        until("the CRM") {
            model.uiState.value.objects.size == 3 && model.uiState.value.crmDetails != null &&
                model.uiState.value.people.isNotEmpty()
        }
        model.exportCsv(listOf("Kennung", "Klasse", "Elternteil"))
        return model.uiState.value.pendingExport?.content.orEmpty().lines()
    }

    @Test
    fun `the fixed headings are the ones the screen gives, in the user's language`() {
        assertEquals("Kennung,Klasse,Elternteil,Name,Owner,Stage", csv().first())
    }

    @Test
    fun `a person is written by name, and by id only when the CRM has no name for them`() {
        val rows = csv().drop(1).associateBy { row -> row.substringBefore(",") }
        assertEquals("a,Company,,Acme,Bob,Lead", rows["a"])
        assertEquals("b,Company,,Globex,zed,", rows["b"])
        assertEquals("c,Company,,Initech,nameless,", rows["c"])
    }
}
