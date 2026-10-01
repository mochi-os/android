// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentDraftsTest {

    private val drafts = CommentDrafts()

    @Test
    fun `a target with nothing typed has an empty draft`() {
        assertEquals(CommentDraft(), drafts.draft("ticket"))
    }

    @Test
    fun `each target keeps its own draft`() {
        drafts.update("first") { it.copy(text = "one") }
        drafts.update("second") { it.copy(text = "two", reply = "comment") }
        assertEquals(CommentDraft(text = "one"), drafts.draft("first"))
        assertEquals(CommentDraft(text = "two", reply = "comment"), drafts.draft("second"))
    }

    @Test
    fun `an update builds on what is already kept`() {
        drafts.update("ticket") { it.copy(text = "typed") }
        drafts.update("ticket") { it.copy(reply = "comment") }
        assertEquals(CommentDraft(text = "typed", reply = "comment"), drafts.draft("ticket"))
    }

    @Test
    fun `clearing drops only that target's draft`() {
        drafts.update("sent") { it.copy(text = "done") }
        drafts.update("other") { it.copy(text = "still typing") }
        drafts.clear("sent")
        assertEquals(CommentDraft(), drafts.draft("sent"))
        assertEquals(CommentDraft(text = "still typing"), drafts.draft("other"))
    }

    @Test
    fun `a comment is not being edited until an edit is kept, and not after it ends`() {
        assertNull(drafts.edit("comment"))
        drafts.edit("comment", "reworded")
        assertEquals("reworded", drafts.edit("comment"))
        drafts.edit("comment", null)
        assertNull(drafts.edit("comment"))
    }

    @Test
    fun `an edit emptied of its text is still an edit`() {
        drafts.edit("comment", "")
        assertEquals("", drafts.edit("comment"))
    }
}
