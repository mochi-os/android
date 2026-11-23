// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the editor counts as unsaved, by the web editor's rule. */
class UnsavedTest {

    private val loaded = PageEditorUiState(
        title = "Home",
        originalTitle = "Home",
        content = "Text",
        originalContent = "Text",
        slug = "home",
    )

    @Test
    fun `a new page holds something once its title or content is not blank`() {
        assertFalse(unsaved(isNew = true, state = PageEditorUiState()))
        assertFalse(unsaved(isNew = true, state = PageEditorUiState(title = "  ", content = "\n")))
        assertTrue(unsaved(isNew = true, state = PageEditorUiState(title = "Plans")))
        assertTrue(unsaved(isNew = true, state = PageEditorUiState(content = "Text")))
    }

    @Test
    fun `an existing page holds something once its title or content differs from what was loaded`() {
        assertFalse(unsaved(isNew = false, state = loaded))
        assertTrue(unsaved(isNew = false, state = loaded.copy(title = "House")))
        assertTrue(unsaved(isNew = false, state = loaded.copy(content = "Text\n")))
    }

    @Test
    fun `spaces around the title and the edit summary are not changes`() {
        assertFalse(unsaved(isNew = false, state = loaded.copy(title = " Home ")))
        assertFalse(unsaved(isNew = false, state = loaded.copy(comment = "Fixed a typo")))
    }

    @Test
    fun `nothing is unsaved once the page is saved`() {
        assertFalse(unsaved(isNew = false, state = loaded.copy(content = "Text, more", saved = true)))
        assertFalse(unsaved(isNew = true, state = PageEditorUiState(title = "Plans", saved = true)))
    }
}
