// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The line under the sync switch reads in the app's language: a refusal the
 * server gave no words for is said from the catalogue, by its kind, and the
 * same for contacts and calendar events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncRefusalTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a refusal without the server's words is said by its kind`() {
        assertEquals(context.getString(R.string.sync_conflict), SyncRefusal(SyncRefusal.Kind.CONFLICT).text(context))
        assertEquals(context.getString(R.string.sync_missing), SyncRefusal(SyncRefusal.Kind.MISSING).text(context))
        assertEquals(context.getString(R.string.sync_refused), SyncRefusal(SyncRefusal.Kind.REFUSED, " ").text(context))
    }

    @Test
    fun `the server's words are said as they are`() {
        assertEquals("Contact name is required", SyncRefusal(SyncRefusal.Kind.REFUSED, "Contact name is required").text(context))
    }

    @Test
    fun `the exceptions carry no English of the client's`() {
        assertEquals(null, SyncConflict().message)
        assertEquals(null, SyncMissing().message)
        assertEquals(null, SyncRefused(null).message)
    }
}
