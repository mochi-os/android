// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * server leaves it alone. Tapping it names the account to sign in.
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
        manager.cancelAll()
        // The app's launcher entry, which the prompt's tap opens.
        val launcher = ComponentName(context.packageName, "org.mochios.Launcher")
        val packages = shadowOf(context.packageManager)
        packages.addActivityIfNotPresent(launcher)
        packages.addIntentFilterForActivity(
            launcher,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private fun prompts(): List<Notification> = shadowOf(manager).allNotifications

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

    @Test
    fun `the prompt's tap names its account`() {
        repeat(3) { Reauth.report401(context, "a1") }
        val tap = shadowOf(prompts().single().contentIntent).savedIntent
        assertEquals("a1", tap.getStringExtra(Reauth.EXTRA_ACCOUNT))
    }
}
