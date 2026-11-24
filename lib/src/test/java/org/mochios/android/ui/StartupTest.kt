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

    // A sign-up left before its identity: core mints for the account, and
    // refuses it every private action until it has one.
    @Test
    fun `an account read as having no identity goes to making one`() {
        val (outcome, _) = run(
            identity = { delay(TRIP); IdentityInfo(Identity("", "", "ann@example.com", ""), AccountStatus("active", 0)) },
        )
        assertEquals(Startup.NeedsIdentity, outcome)
    }

    // Core refuses a token to a session with no identity: restarting between
    // the sign-up code and naming the account landed in an app whose every
    // request failed.
    @Test
    fun `a mint refused for want of an identity goes to making one at once`() {
        val (outcome, took) = run(
            mint = { delay(TRIP); Result.failure(ApiException(403, ApiError("identity_required"))) },
            identity = { delay(10 * TRIP); person() },
            preferences = { delay(10 * TRIP); warmed.set(true) },
        )
        assertEquals(Startup.NeedsIdentity, outcome)
        assertTrue("took $took ms", took < 2 * TRIP)
        assertFalse(warmed.get())
    }

    @Test
    fun `any other 403 from the mint goes on with the cached credentials`() {
        val refused = ApiException(403, ApiError("forbidden"))
        val (outcome, _) = run(mint = { delay(TRIP); Result.failure(refused) })
        assertEquals(Startup.Continue(null, unreachable = true, failure = refused), outcome)
    }

    @Test
    fun `a 401 signs out whatever its code`() {
        val (outcome, _) = run(mint = { delay(TRIP); Result.failure(ApiException(401, ApiError("identity_required"))) })
        assertEquals(Startup.SignedOut, outcome)
    }

    @Test
    fun `an identity read that failed is not taken for none`() {
        val (outcome, _) = run(identity = { delay(TRIP); null })
        assertEquals(Startup.Continue(null), outcome)
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
