// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSignalTest {
    @Test
    fun readsTheSyncASignalAsksFor() {
        assertEquals("calendars", SyncSignal.kind(mapOf("sync" to "calendars")))
        assertEquals("contacts", SyncSignal.kind(mapOf("sync" to "contacts")))
    }

    @Test
    fun leavesAnOrdinaryNotificationAlone() {
        assertNull(SyncSignal.kind(mapOf("title" to "Reminder test", "body" to "Starts at 12:05")))
        assertNull(SyncSignal.kind(mapOf("sync" to "")))
    }
}
