// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.websocket.REOPEN
import org.mochios.android.websocket.StreamWsEvent

/**
 * A frame on the `staff-events` stream stands for the event its topic names;
 * the stream opening again after being down stands for one every list reads
 * again on, having missed whatever was sent meanwhile.
 */
class StaffEventTest {

    private fun frame(type: String = "", topic: String? = null) = StreamWsEvent(
        type = type,
        created = 0,
        member = null,
        name = null,
        body = null,
        event = null,
        raw = if (topic == null) emptyMap() else mapOf("topic" to topic),
    )

    @Test
    fun `a topic stands for its event`() {
        assertEquals(StaffEvent.NewReport, staffEvent(frame(topic = "staff/report")))
    }

    @Test
    fun `the stream opening again stands for an event every list reads again on`() {
        assertEquals(StaffEvent.Unknown(REOPEN), staffEvent(frame(type = REOPEN)))
    }

    @Test
    fun `a frame with no topic stands for nothing`() {
        assertNull(staffEvent(frame()))
    }
}
