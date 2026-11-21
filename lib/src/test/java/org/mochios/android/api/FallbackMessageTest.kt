// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A failed action says the server's reason when it gave one, why the request
 * never reached it when it did not, and otherwise which action failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FallbackMessageTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun begin() {
        AppContext.set(context)
    }

    @Test
    fun `no connection says so, not which action failed`() {
        assertEquals(
            "THE POINT",
            context.getString(R.string.error_network),
            MochiError.NetworkError().messageOrFallback("Failed to make move"),
        )
    }

    @Test
    fun `a local check says its own line`() {
        assertEquals(
            context.getString(R.string.error_unexpected),
            MochiError.Local(R.string.error_unexpected).messageOrFallback("Failed to make move"),
        )
    }

    @Test
    fun `the server's reason is said as it gave it`() {
        assertEquals("Not your turn", MochiError.ForbiddenError("Not your turn").messageOrFallback("Failed to make move"))
        assertEquals("Game is over", MochiError.ServerError(500, "Game is over").messageOrFallback("Failed to make move"))
    }

    @Test
    fun `a refusal without a reason names the action`() {
        assertEquals("Failed to make move", MochiError.NotFoundError().messageOrFallback("Failed to make move"))
        assertEquals("Failed to make move", MochiError.ServerError(502).messageOrFallback("Failed to make move"))
        assertEquals("Failed to make move", MochiError.Unknown(" ").messageOrFallback("Failed to make move"))
    }
}
