// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.staff.R
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An object's history arrives a page at a time: Load more asks for the page
 * after the cursor the last one gave, an entry it repeats is listed once, and
 * the button goes once the server says there is no more.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuditTimelinePagingTest {

    @get:Rule
    val rule = createComposeRule()

    private val server = StaffServer { request ->
        val url = request.requestUrl!!
        if (!url.encodedPath.endsWith("-/audit/object")) return@StaffServer null
        when (url.queryParameter("after")) {
            "c2" -> ok("""{"audit": [{"id": "e2", "action": "order.paid"}, {"id": "e3", "action": "order.shipped"}], "total": 3, "next": ""}""")
            else -> ok("""{"audit": [{"id": "e1", "action": "order.created"}, {"id": "e2", "action": "order.paid"}], "total": 3, "next": "c2"}""")
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    @Test
    fun `Load more reads the page after the cursor and lists each entry once`() {
        val model = AuditTimelineHostViewModel(server.repository)
        model.load("order", "o1")
        server.until { !model.state.value.isLoading }
        assertEquals(listOf("e1", "e2"), model.state.value.entries.map { entry -> entry.id })
        assertNull(server.made("-/audit/object").single().requestUrl!!.queryParameter("after"))

        model.loadMore()
        server.until { model.state.value.entries.size == 3 }
        assertEquals("c2", server.made("-/audit/object").last().requestUrl!!.queryParameter("after"))
        assertEquals(listOf("e1", "e2", "e3"), model.state.value.entries.map { entry -> entry.id })
        assertFalse(model.state.value.paging.more)
    }

    @Test
    fun `the timeline offers Load more while there is a next page`() {
        val model = AuditTimelineHostViewModel(server.repository)
        model.load("order", "o1")
        server.until { !model.state.value.isLoading }
        rule.setContent {
            val state = model.state.value
            AuditTimelineBody(state = state, onMore = model::loadMore)
        }
        val more = server.context.getString(R.string.staff_audit_load_more)
        rule.onNodeWithText(more).performClick()
        server.until { model.state.value.entries.size == 3 }
        assertEquals(2, server.made("-/audit/object").size)
    }
}
