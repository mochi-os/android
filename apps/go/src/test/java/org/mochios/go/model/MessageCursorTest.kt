// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mochios.android.api.ApiClient

/**
 * `nextCursor` is `"<created>:<id>"`, not a timestamp — `created` alone is not
 * unique, and paginating on it drops every row sharing the page boundary's
 * second.
 *
 * Typed `Long?`, Gson aborted the whole payload, and because the field is only
 * populated when `hasMore` is true the failure appeared only once a game passed
 * the page limit: the chat and move log went permanently empty with no error.
 * Parsed with the app's real Gson configuration so the test fails the same way
 * production did.
 */
class MessageCursorTest {

    private val gson = ApiClient.provideGson()

    private val page =
        """{"messages":[],"hasMore":true,"nextCursor":"1753900000:0K3xQ9"}"""

    @Test
    fun `composite cursor parses`() {
        val parsed = gson.fromJson(page, GetMessagesResponse::class.java)
        assertEquals("1753900000:0K3xQ9", parsed.nextCursor)
        assertEquals(true, parsed.hasMore)
    }

    @Test
    fun `last page has no cursor`() {
        val parsed = gson.fromJson(
            """{"messages":[],"hasMore":false}""",
            GetMessagesResponse::class.java,
        )
        assertNull(parsed.nextCursor)
    }

    /**
     * Control. The previous `Long?` typing is reproduced locally to prove this
     * payload really is what broke — without it the assertions above would pass
     * just as happily against the old declaration.
     */
    private data class LegacyShape(val nextCursor: Long? = null)

    @Test
    fun `the previous Long typing could not parse this payload`() {
        assertThrows(JsonSyntaxException::class.java) {
            gson.fromJson(page, LegacyShape::class.java)
        }
    }
}
