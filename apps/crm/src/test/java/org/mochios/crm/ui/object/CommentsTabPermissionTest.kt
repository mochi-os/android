// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.CommentDrafts
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Who sees Edit and Delete on a comment: its author alone, as the server refuses
 * anyone else's delete, a manager's included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CommentsTabPermissionTest {

    @get:Rule
    val rule = createComposeRule()

    private val edit = RuntimeEnvironment.getApplication().getString(MochiR.string.common_edit)
    private val delete = RuntimeEnvironment.getApplication().getString(MochiR.string.common_delete)

    private fun show(me: String?) {
        rule.setContent {
            CommentsTab(
                comments = listOf(Comment(id = "first", author = "ann", body = "as posted", authorName = "Ann")),
                currentUserId = me,
                crmId = "crm",
                drafts = CommentDrafts(),
                target = "ticket",
                onCreateComment = { _, _, _ -> },
                resolveFileName = { "" },
                onUpdateComment = { _, _ -> },
                onDeleteComment = {},
            )
        }
    }

    @Test
    fun `the author may edit and delete their comment`() {
        show(me = "ann")
        rule.onNodeWithContentDescription(edit).assertExists()
        rule.onNodeWithContentDescription(delete).assertExists()
    }

    @Test
    fun `anyone else may do neither`() {
        show(me = "bob")
        rule.onNodeWithContentDescription(edit).assertDoesNotExist()
        rule.onNodeWithContentDescription(delete).assertDoesNotExist()
    }
}
