// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.api.ApiError
import org.mochios.android.api.ApiException
import org.mochios.android.auth.AccountStatus
import org.mochios.android.auth.Identity
import org.mochios.android.auth.IdentityInfo
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A cold start's requests - the app token's mint, the identity read and the
 * preferences warm-up - each a round trip, used to go out one after another
 * behind the spinner. Each fake here takes [TRIP] ms, as a round trip to a
 * distant server would: in series they would take three of them.
 */
class StartupTest {
    private val warmed = AtomicBoolean(false)

    private fun person(status: String = "active") =
        IdentityInfo(Identity("i1", "Ann", "ann@example.com", "fp"), AccountStatus(status, 99))

    private fun run(
        mint: suspend () -> Result<String> = { delay(TRIP); Result.success("token") },
        identity: suspend () -> IdentityInfo? = { delay(TRIP); person() },
        preferences: suspend () -> Unit = { delay(TRIP); warmed.set(true) },
    ): Pair<Startup, Long> = runBlocking {
        val start = System.nanoTime()
        val outcome = startup(mint, identity, preferences)
        outcome to (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun `the three go out together, the preferences waited for, in one round trip rather than three`() {
        val (outcome, took) = run()
        assertEquals(Startup.Continue(person()), outcome)
        assertTrue(warmed.get())
        assertTrue("took $took ms", took < 2 * TRIP)
    }

    @Test
    fun `a 401 from the mint signs out at once, the rest abandoned`() {
        val (outcome, took) = run(
            mint = { delay(TRIP); Result.failure(ApiException(401, ApiError("unauthorized"))) },
            identity = { delay(10 * TRIP); person() },
            preferences = { delay(10 * TRIP); warmed.set(true) },
        )
        assertEquals(Startup.SignedOut, outcome)
        assertTrue("took $took ms", took < 2 * TRIP)
        assertFalse(warmed.get())
    }

    @Test
    fun `a closing account shows the interstitial without waiting for the preferences`() {
        val (outcome, took) = run(
            identity = { delay(TRIP); person("closing") },
            preferences = { delay(10 * TRIP); warmed.set(true) },
        )
        assertEquals(Startup.Closing(99), outcome)
        assertTrue("took $took ms", took < 2 * TRIP)
        assertFalse(warmed.get())
    }

    @Test
    fun `an unreachable server is not waited on any further`() {
        val (outcome, took) = run(
            mint = { delay(TRIP); Result.failure(IOException("unreachable")) },
            identity = { delay(10 * TRIP); person() },
            preferences = { delay(10 * TRIP); warmed.set(true) },
        )
        assertTrue(outcome is Startup.Continue && outcome.unreachable && outcome.identity == null)
        assertTrue("took $took ms", took < 2 * TRIP)
        assertFalse(warmed.get())
    }

    private companion object {
        const val TRIP = 500L
    }
}
