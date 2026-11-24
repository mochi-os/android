// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.websocket.REOPEN

/**
 * A frame on the `staff-events` socket stands for the event its topic names;
 * the socket opening again after being down stands for one every list reads
 * again on, having missed whatever was sent meanwhile.
 */
class StaffEventTest {

    @Test
    fun `a topic stands for its event`() {
        assertEquals(StaffEvent.NewReport, staffEvent(WebSocketEvent(topic = "staff/report")))
        assertEquals(StaffEvent.ModerationUpdated, staffEvent(WebSocketEvent(topic = "staff/moderation")))
        assertEquals(StaffEvent.NewDispute, staffEvent(WebSocketEvent(topic = "staff/dispute")))
    }

    @Test
    fun `the socket opening again stands for an event every list reads again on`() {
        assertEquals(StaffEvent.Unknown(REOPEN), staffEvent(WebSocketEvent(type = REOPEN)))
    }

    @Test
    fun `a frame with no topic stands for nothing`() {
        assertNull(staffEvent(WebSocketEvent()))
    }
}
