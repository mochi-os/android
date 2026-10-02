// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.ui.editor.EditorUiState
import org.mochios.calendars.ui.editor.dirty
import org.mochios.calendars.ui.editor.form

/** Whether leaving the editor would drop something, measured against the form as it was opened. */
class EventDirtyTest {

    private val user = "Europe/London"

    private fun opened(state: EditorUiState) = state.copy(opened = form(state, user) to state.calendar)

    private val stored = opened(
        EditorUiState(calendar = "work", title = "Stand-up", start = 1_790_067_600, finish = 1_790_071_200, isLoading = false),
    )

    @Test
    fun `a form as it was opened is no change`() {
        assertFalse(dirty(stored, user))
    }

    @Test
    fun `a typed title is a change`() {
        assertTrue(dirty(stored.copy(title = "Stand-up, moved"), user))
    }

    @Test
    fun `an edit undone by hand is no change`() {
        assertFalse(dirty(stored.copy(title = "Stand-up, moved").copy(title = "Stand-up"), user))
    }

    @Test
    fun `another calendar is a change`() {
        assertTrue(dirty(stored.copy(calendar = "home"), user))
    }

    @Test
    fun `a colour or a link is a change`() {
        assertTrue(dirty(stored.copy(colour = "#f87171"), user))
        assertTrue(dirty(stored.copy(url = "https://example.org"), user))
    }

    @Test
    fun `nothing is dropped before the form has opened`() {
        assertFalse(dirty(EditorUiState(title = "Stand-up"), user))
    }
}
