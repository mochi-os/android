// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The home grid and pinned shortcuts open an app as a home screen does: main
 * and launcher, at that app's launcher activity, in a task of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchersTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `an app opens at its own launcher activity in a new task`() {
        val intent = launcherIntentFor(context, "feeds")!!
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals("${context.packageName}.MochiFeedsLauncher", intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `the name is matched whatever its case`() {
        assertEquals(
            "${context.packageName}.MochiCalendarsLauncher",
            launcherIntentFor(context, "Calendars")?.component?.className,
        )
    }

    @Test
    fun `an app with no module cannot be opened`() {
        assertFalse(launchable("air"))
        assertFalse(launchable(null))
        assertNull(launcherIntentFor(context, "air"))
    }

    @Test
    fun `every app with a module, and the home grid, can be opened`() {
        for (app in listOf("home", "feeds", "chat", "staff", "calendars")) {
            assertTrue(app, launchable(app))
        }
    }
}
