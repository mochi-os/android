// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.feeds.model.SavedSnapshot

class SavedSnapshotDefaultsTest {

    // The web's snapshot of a two-image post: no caption on either image, and
    // an explicit null name on the second.
    private val json = """
        {"id": "p1", "attachments": [
            {"id": "a1", "name": "one.jpg", "type": "image/jpeg"},
            {"id": "a2", "name": null, "type": "image/png"}
        ]}
    """.trimIndent()

    @Test
    fun `gson leaves a missing caption null, which is what crashed the mosaic`() {
        val raw = Gson().fromJson(json, SavedSnapshot::class.java)
        assertNull(raw.attachments[0].caption as String?)
    }

    @Test
    fun `a snapshot attachment missing its caption comes back with an empty one`() {
        val snapshot = Gson().fromJson(json, SavedSnapshot::class.java).withAttachmentDefaults()
        assertEquals("", snapshot.attachments[0].caption)
        assertEquals("", snapshot.attachments[1].caption)
    }

    @Test
    fun `a null name becomes empty and the fields that were there are kept`() {
        val snapshot = Gson().fromJson(json, SavedSnapshot::class.java).withAttachmentDefaults()
        assertEquals("one.jpg", snapshot.attachments[0].name)
        assertEquals("", snapshot.attachments[1].name)
        assertEquals("image/png", snapshot.attachments[1].type)
    }
}
