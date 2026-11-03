// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

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
}
