// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

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
 * Who sees Edit and Delete on a comment: Edit for its author alone, Delete for
 * its author or a manager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CommentsTabPermissionTest {

    @get:Rule
    val rule = createComposeRule()

    private val edit = RuntimeEnvironment.getApplication().getString(MochiR.string.common_edit)
    private val delete = RuntimeEnvironment.getApplication().getString(MochiR.string.common_delete)

    private fun show(me: String?, manager: Boolean) {
        rule.setContent {
            CommentsTab(
                comments = listOf(Comment(id = "first", author = "ann", body = "as posted", authorName = "Ann")),
                currentUserId = me,
                canManage = manager,
                projectId = "project",
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
        show(me = "ann", manager = false)
        rule.onNodeWithContentDescription(edit).assertExists()
        rule.onNodeWithContentDescription(delete).assertExists()
    }

    @Test
    fun `a manager may delete another person's comment but not edit it`() {
        show(me = "bob", manager = true)
        rule.onNodeWithContentDescription(edit).assertDoesNotExist()
        rule.onNodeWithContentDescription(delete).assertExists()
    }

    @Test
    fun `anyone else may do neither`() {
        show(me = "bob", manager = false)
        rule.onNodeWithContentDescription(edit).assertDoesNotExist()
        rule.onNodeWithContentDescription(delete).assertDoesNotExist()
    }
}
