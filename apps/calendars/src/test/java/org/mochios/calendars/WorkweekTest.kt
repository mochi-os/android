// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.calendars.storage.VisibilityStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The work week is kept on the device, as the web keeps it in the browser. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkweekTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the work week is off until it is turned on`() {
        assertFalse(VisibilityStore.workweek(context))
    }

    @Test
    fun `the work week chosen is there on the next launch`() {
        VisibilityStore.workweek(context, true)
        assertTrue(VisibilityStore.workweek(context))
        VisibilityStore.workweek(context, false)
        assertFalse(VisibilityStore.workweek(context))
    }
}
