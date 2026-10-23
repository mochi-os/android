// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.push.NonceStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A notification tapped in one app's list opens in the task of the app it
 * belongs to, as a tapped system notification does, rather than stacking that
 * app's screens inside the one it was tapped in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenInAppTest {

    private val context = RuntimeEnvironment.getApplication()
    private val shadow get() = shadowOf(context)

    @Before
    fun begin() {
        while (shadow.nextStartedActivity != null) {
            // Drop launches an earlier test left behind.
        }
    }

    @Test
    fun `a link for another app opens in that app's task`() {
        assertTrue(openInApp(context, "/market/listings/l1/messages/t1", "chat"))
        val started = shadow.nextStartedActivity
        assertEquals("${context.packageName}.MochiMarketLauncher", started.component?.className)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val uri = started.data!!
        assertEquals("mochi", uri.scheme)
        val query = uri.encodedSchemeSpecificPart.substringAfter("notification?")
        val params = query.split('&').associate { pair ->
            pair.substringBefore('=') to Uri.decode(pair.substringAfter('='))
        }
        assertEquals("/market/listings/l1/messages/t1", params["link"])
        // The receiving activity accepts the link only with a nonce this app
        // issued, as it does for a system notification.
        assertTrue(NonceStore(context).consume(params["nonce"]))
    }

    @Test
    fun `a link for the app it was tapped in is left to that app`() {
        assertFalse(openInApp(context, "/chat/c1", "chat"))
        assertNull(shadow.nextStartedActivity)
    }

    @Test
    fun `the app is matched whatever its case`() {
        assertFalse(openInApp(context, "/Chat/c1", "chat"))
        assertNull(shadow.nextStartedActivity)
    }

    @Test
    fun `a link no app here hosts is left to the caller`() {
        assertFalse(openInApp(context, "/air/game", "chat"))
        assertFalse(openInApp(context, "https://example.org/post", "chat"))
        assertFalse(openInApp(context, "", "chat"))
        assertNull(shadow.nextStartedActivity)
    }
}
