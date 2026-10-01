// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.settings.api.NotifCategory

class CategoryLabelTest {

    /** A seeded category stores English; the server sends its translation as display. */
    @Test
    fun `a category shows its display name`() {
        val category = Gson().fromJson(
            """{"id":"1","label":"Normal","display":"Normale","default":1}""",
            NotifCategory::class.java,
        )
        assertEquals("Normale", category.shown)
    }

    @Test
    fun `a category without a display name shows its label`() {
        val category = Gson().fromJson("""{"id":"x","label":"Work"}""", NotifCategory::class.java)
        assertEquals("Work", category.shown)
    }

    /** Saving an untouched seeded category must not store the translation as its label. */
    @Test
    fun `an untouched name sends no label`() {
        assertNull(categoryLabelChange("Normale", "Normale"))
    }

    @Test
    fun `a name changed only by surrounding space sends no label`() {
        assertNull(categoryLabelChange(" Normale ", "Normale"))
    }

    @Test
    fun `a changed name sends it trimmed`() {
        assertEquals("Urgent", categoryLabelChange(" Urgent ", "Normale"))
    }

    /** A new category opens on "", so any name typed is a change. */
    @Test
    fun `a new category sends its name`() {
        assertEquals("Work", categoryLabelChange("Work", ""))
    }
}
