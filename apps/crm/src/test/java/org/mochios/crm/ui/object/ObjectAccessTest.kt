// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.crm.CrmServer
import org.mochios.crm.R
import org.mochios.crm.model.Crm
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * An item's sheet offers what the user's access lets them do, as the web's
 * panel does: Copy and Delete, and adding or deleting files, to those who may
 * write; the comment composer and replies to those who may comment; to a
 * viewer neither, though the files and comments there are shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class ObjectAccessTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer
    private lateinit var socket: MochiWebSocket

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("c1/-/objects/o1", """{"object": {"id": "o1", "crm": "c1", "class": "company"}, "values": {"name": "Acme"}}""")
        server.data("c1/-/objects/o1/comments", """{"comments": [{"id": "k1", "author": "ann", "body": "Hello", "name": "Ann"}]}""")
        socket = server.socket()
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.stop()
    }

    private lateinit var model: ObjectDetailViewModel

    private fun sheet(access: String) {
        model = ObjectDetailViewModel(server.repository(), server.session, socket)
        val details = CrmDetails(
            crm = Crm(id = "c1", name = "Sales", access = access),
            classes = listOf(CrmClass(id = "company", name = "Company", title = "name")),
            fields = mapOf("company" to listOf(CrmField(id = "name", name = "Name", fieldtype = "text"))),
        )
        rule.setContent {
            ObjectDetailSheet(
                crmId = "c1",
                objectId = "o1",
                crmDetails = details,
                onDismiss = {},
                onDeleteObject = {},
                viewModel = model,
            )
        }
        until("the item") { model.uiState.value.obj?.id == "o1" }
        rule.waitForIdle()
    }

    private fun offered(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun described(text: String) =
        rule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

    private fun comments() {
        until("the comments") { model.uiState.value.comments.isNotEmpty() }
        rule.onNodeWithText(context.getString(R.string.crm_object_tab_comments)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `a writer may copy, delete and add files`() {
        sheet("write")
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_more_options)).performClick()
        rule.waitForIdle()
        assertTrue(offered(context.getString(MochiR.string.common_copy)))
        assertTrue(offered(context.getString(MochiR.string.common_delete)))
        assertTrue(described(context.getString(R.string.crm_attachment_add)))
    }

    @Test
    fun `a viewer may neither copy nor delete, nor add files, nor comment`() {
        sheet("view")
        assertFalse(described(context.getString(MochiR.string.common_more_options)))
        assertFalse(described(context.getString(R.string.crm_attachment_add)))
        assertFalse("no files and no way to add one: no section", offered(context.getString(R.string.crm_attachments)))
        comments()
        assertFalse(offered(context.getString(R.string.crm_comment_placeholder)))
        assertFalse(described(context.getString(MochiR.string.comment_reply)))
    }

    @Test
    fun `a commenter may comment and reply but not change the item`() {
        sheet("comment")
        assertFalse(described(context.getString(MochiR.string.common_more_options)))
        comments()
        assertTrue(offered(context.getString(R.string.crm_comment_placeholder)))
        assertEquals(1, rule.onAllNodesWithContentDescription(context.getString(MochiR.string.comment_reply)).fetchSemanticsNodes().size)
    }
}
