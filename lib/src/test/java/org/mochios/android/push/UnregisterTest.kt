// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Sign-out retires the UnifiedPush registration made for the signed-in
 * identity, which is the instance PushTransport registered - not the package
 * name, which was never registered, so the distributor kept a row for every
 * identity ever signed in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnregisterTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var sessions: SessionManager

    @Before
    fun begin() {
        FakeKeystore.install()
        sessions = SessionManager(context)
        runBlocking { sessions.clearAll() }
    }

    @Test
    fun `sign-out unregisters the bound identity`() {
        val retired = mutableListOf<String>()
        runBlocking {
            sessions.setBoundAccount("i1")
            MochiPushClient.unregisterBound(context, sessions) { instance -> retired += instance }
        }
        assertEquals(listOf("i1"), retired)
    }

    @Test
    fun `with no identity bound nothing is unregistered`() {
        val retired = mutableListOf<String>()
        runBlocking { MochiPushClient.unregisterBound(context, sessions) { instance -> retired += instance } }
        assertEquals(emptyList<String>(), retired)
    }
}
