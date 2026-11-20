// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A dead session is told to the user once per account, quietly after the
 * first sound, and a prompt dismissed stays dismissed however often the
 * session is refused after. Another account's success or failure on the same
 * server leaves it alone. Tapping it names the account to sign in, with a
 * nonce that proves the tap came from the prompt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReauthTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun begin() {
        Reauth.reportSuccess(context, "a1")
        Reauth.reportSuccess(context, "a2")
        Reauth.reportSuccess(context, "a 1")
        manager.cancelAll()
        shadowOf(manager).setNotificationsEnabled(true)
        context.getSharedPreferences("mochi_notification_taps", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun prompts(): List<Notification> = shadowOf(manager).allNotifications

    /** The query of the prompt's `mochi:reauth?...` URI. */
    private fun query(tap: Intent): Map<String, String> {
        val uri = tap.data!!
        assertEquals("mochi", uri.scheme)
        val part = uri.encodedSchemeSpecificPart
        assertEquals("reauth", part.substringBefore('?'))
        return part.substringAfter('?').split('&').associate { pair ->
            pair.substringBefore('=') to Uri.decode(pair.substringAfter('='))
        }
    }

    @Test
    fun `a dead session is told once`() {
        val dead = (1..3).map { Reauth.report401(context, "a1") }
        assertEquals(listOf(false, false, true), dead)
        assertEquals(1, prompts().size)
        assertTrue("alerts once", prompts().single().flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        // Dismissed; the session is refused twice more.
        manager.cancelAll()
        assertEquals(listOf(true, true), (1..2).map { Reauth.report401(context, "a1") })
        assertEquals("not posted again", 0, prompts().size)
    }

    @Test
    fun `another account on the same server leaves a dead account's prompt and count alone`() {
        repeat(3) { Reauth.report401(context, "a1") }
        Reauth.reportSuccess(context, "a2")
        assertEquals("the dead account's prompt stays", 1, prompts().size)
        assertFalse("a second account's first refusal is no dead session", Reauth.report401(context, "a2"))
    }

    /**
     * The tap is a `mochi:` URI to the home grid's activity, which any app can
     * send: only the nonce the prompt was posted with makes it count, and
     * only once.
     */
    @Test
    fun `the prompt's tap names its account with a nonce`() {
        repeat(3) { Reauth.report401(context, "a 1") }
        val tap = shadowOf(prompts().single().contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, tap.action)
        assertEquals("${context.packageName}.MochiHomeLauncher", tap.component?.className)
        assertTrue("carries no extra to trust", tap.extras == null || tap.extras!!.isEmpty)
        val query = query(tap)
        assertEquals("a 1", query["account"])
        val nonces = NonceStore(context)
        assertTrue(nonces.consume(query["nonce"]))
        assertFalse("spent", nonces.consume(query["nonce"]))
    }

    /** A prompt the user has turned notifications off for holds no nonce. */
    @Test
    fun `a prompt that is not shown issues no nonce`() {
        shadowOf(manager).setNotificationsEnabled(false)
        repeat(3) { Reauth.report401(context, "a1") }
        assertEquals(0, prompts().size)
        val stored = context.getSharedPreferences("mochi_notification_taps", Context.MODE_PRIVATE)
            .getString("nonces", "").orEmpty()
        assertEquals(emptyList<Outstanding>(), noncesFromStored(stored))
    }

    /** The session coming back takes the prompt away, and its tap with it. */
    @Test
    fun `a session that comes back retires the prompt's nonce`() {
        repeat(3) { Reauth.report401(context, "a1") }
        val nonce = query(shadowOf(prompts().single().contentIntent).savedIntent)["nonce"]
        Reauth.reportSuccess(context, "a1")
        assertNull("cancelled", prompts().firstOrNull())
        assertFalse(NonceStore(context).consume(nonce))
    }
}
