// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.lib

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What an account viewed and reported on this phone is its own: the next
 * account to sign in is not shown it, and it is gone once that account has
 * signed in, as the previous account is forgotten on the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountStoresTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var session: SessionManager

    @Before
    fun begin() {
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl("https://example.org")
            session.setBoundAccount("first", "https://example.org")
            // The store outlives a test; start each with nothing viewed.
            RecentlyViewedStore(context, session).clear()
        }
    }

    /** Polls [read] until it answers [expected], or fails after five seconds. */
    private fun <T> eventually(expected: T, read: suspend () -> T) {
        val deadline = System.currentTimeMillis() + 5_000
        var last: T
        do {
            last = runBlocking { read() }
            if (last == expected) return
            Thread.sleep(20)
        } while (System.currentTimeMillis() < deadline)
        assertEquals(expected, last)
    }

    @Test
    fun `another account is not shown what the previous one viewed or reported`() {
        val recent = RecentlyViewedStore(context, session)
        val reported = ReportedStore(context, session)
        runBlocking {
            recent.push("l1")
            reported.markReported("l2")
        }
        eventually(listOf("l1")) { recent.observe().first() }
        eventually(setOf("l2")) { reported.observe().first() }
        runBlocking { session.setBoundAccount("second", "https://example.org") }
        eventually(emptyList<String>()) { recent.observe().first() }
        eventually(emptySet<String>()) { reported.observe().first() }
    }

    @Test
    fun `the previous account's history is forgotten, not kept for its return`() {
        val recent = RecentlyViewedStore(context, session)
        runBlocking { recent.push("l1") }
        eventually(listOf("l1")) { recent.observe().first() }
        runBlocking { session.setBoundAccount("second", "https://example.org") }
        eventually(emptyList<String>()) { recent.observe().first() }
        runBlocking { recent.push("l9") }
        eventually(listOf("l9")) { recent.observe().first() }
        runBlocking { session.setBoundAccount("first", "https://example.org") }
        eventually(emptyList<String>()) { recent.observe().first() }
    }
}
