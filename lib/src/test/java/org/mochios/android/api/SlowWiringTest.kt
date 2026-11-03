// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import androidx.test.core.app.ApplicationProvider
import okhttp3.Interceptor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The client every module's API is built from carries [slowInterceptor], so a
 * call marked [Slow] in any of them waits for the server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SlowWiringTest {

    @Test
    fun `the shared client lets a slow call wait`() {
        val pass = Interceptor { chain -> chain.proceed(chain.request()) }
        val client = ApiClient.provideOkHttpClient(pass, pass, pass, SessionManager(ApplicationProvider.getApplicationContext()))
        assertTrue("the shared client has no slow interceptor", client.interceptors.contains(slowInterceptor))
    }
}
