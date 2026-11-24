// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.team

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Two people of one name are told apart by their fingerprints in the picker,
 * and the one picked is shown by name and fingerprint before the role is
 * granted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddTeamMemberTest {

    @get:Rule
    val rule = createComposeRule()

    private val server = StaffServer { request ->
        val path = request.requestUrl!!.encodedPath
        when {
            path.endsWith("-/directory/search") -> ok(
                """{"results": [{"id": "p1", "name": "Ann", "fingerprint": "abc123defXYZ"},
                                {"id": "p2", "name": "Ann", "fingerprint": "xyz789uvwABC"}]}""",
            )
            path.endsWith("-/team/add") -> ok("""{"id": "p1", "role": "moderator"}""")
            else -> null
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun text(id: Int) = server.context.getString(id)

    @Test
    fun `the picker shows each result's fingerprint, and the grant confirms it`() {
        val model = AddTeamMemberViewModel(server.repository)
        var added = false
        rule.setContent { AddTeamMemberScreen(onBack = {}, onAdded = { added = true }, viewModel = model) }
        rule.onNode(hasSetTextAction()).performTextInput("Ann")
        server.until { model.state.value.results.size == 2 }
        rule.waitForIdle()
        rule.onNodeWithText("abc-123-def").assertExists()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("xyz-789-uvw"))
        rule.onNodeWithText("xyz-789-uvw").performClick()
        rule.onNodeWithText(text(R.string.staff_team_dialog_role_placeholder)).performClick()
        rule.onNodeWithText(text(R.string.staff_team_role_moderator)).performClick()
        rule.onNode(hasText(text(R.string.staff_team_dialog_add)) and hasClickAction()).performClick()

        rule.onNode(hasText("xyz-789-uvw") and hasAnyAncestor(isDialog())).assertExists()
        rule.onNode(hasText("Ann") and hasAnyAncestor(isDialog())).assertExists()
        assertEquals(0, server.made("-/team/add").size)

        rule.onNode(hasText(text(R.string.staff_team_dialog_add)) and hasAnyAncestor(isDialog())).performClick()
        server.until { server.made("-/team/add").isNotEmpty() }
        val sent = server.made("-/team/add").single().body.readUtf8()
        assertEquals("id=p2&role=moderator", sent)
        server.until { model.state.value.added }
        rule.waitForIdle()
        assertEquals(true, added)
    }
}
