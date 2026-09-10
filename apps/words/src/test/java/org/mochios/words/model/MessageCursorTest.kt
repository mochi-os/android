// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mochios.android.api.ApiClient

/**
 * `cursor` is `"<created>:<id>"`, a string; typed `Long?` Gson rejected the
 * whole page once a game passed the page limit. Parsed with the app's real Gson
 * configuration.
 */
class MessageCursorTest {

    private val gson = ApiClient.provideGson()

    private val page =
        """{"messages":[],"more":true,"cursor":"1753900000:0K3xQ9"}"""

    @Test
    fun `composite cursor parses`() {
        val parsed = gson.fromJson(page, GetMessagesResponse::class.java)
        assertEquals("1753900000:0K3xQ9", parsed.cursor)
        assertEquals(true, parsed.more)
    }

    @Test
    fun `last page has no cursor`() {
        val parsed = gson.fromJson(
            """{"messages":[],"more":false}""",
            GetMessagesResponse::class.java,
        )
        assertNull(parsed.cursor)
    }

    /**
     * Control: the old `Long?` typing must fail on the same payload.
     */
    private data class LegacyShape(val cursor: Long? = null)

    @Test
    fun `the previous Long typing could not parse this payload`() {
        assertThrows(JsonSyntaxException::class.java) {
            gson.fromJson(page, LegacyShape::class.java)
        }
    }
}
