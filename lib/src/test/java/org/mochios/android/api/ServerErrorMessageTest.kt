// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/**
 * A 5xx the server explains is shown as the server says it, as the web shows
 * it: a labelled refusal is already translated, and an unhandled failure
 * carries the translated "Server error" with what failed. Only a body that
 * says nothing falls back to the generic line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServerErrorMessageTest {

    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `a labelled refusal shows the server's message`() {
        val error = ApiException(502, ApiError(error = "Feed returned status 404")).toMochiError()
        assertEquals("Feed returned status 404", error.userMessage())
    }

    @Test
    fun `an unhandled failure shows the server's message over its code`() {
        val error = ApiException(500, ApiError(error = "server_error", message = "Server error: no such table")).toMochiError()
        assertEquals("Server error: no such table", error.userMessage())
    }

    @Test
    fun `a body that says nothing gets the generic line with its code`() {
        val text = ApiException(503, ApiError()).toMochiError().userMessage()
        assertTrue(text, "503" in text)
    }

    /**
     * A refusal of any other status keeps it, so a caller can tell one apart:
     * chat's send refuses a message that would go unencrypted with a 409.
     */
    @Test
    fun `a refusal of another status keeps its status, and its code where the server sent one`() {
        val labelled = ApiException(409, ApiError(error = "The message can no longer be encrypted")).toMochiError()
        assertEquals("THE POINT", MochiError.Unknown("The message can no longer be encrypted", 409), labelled)
        assertEquals("The message can no longer be encrypted", labelled.userMessage())
        val coded = ApiException(429, ApiError(error = "rate_limited", message = "Too many requests")).toMochiError()
        assertEquals(MochiError.Unknown("Too many requests", 429, "rate_limited"), coded)
        val bare = HttpException(Response.error<Any>(409, "".toResponseBody(null))).toMochiError()
        assertEquals(MochiError.Unknown(code = 409), bare)
    }
}
