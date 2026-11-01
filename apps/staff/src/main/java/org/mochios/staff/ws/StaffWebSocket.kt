// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ws

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.mochios.android.websocket.REOPEN
import org.mochios.android.websocket.StreamWsEvent
import org.mochios.android.websocket.rememberStreamWebSocket

/**
 * Opens the `staff-events` WebSocket and republishes decoded `{topic, object}`
 * payloads to [eventsBus]. Mount once near the top of the nav graph - the
 * socket closes on dispose.
 */
@Composable
fun rememberStaffEventsSubscription(eventsBus: StaffEventsBus) {
    val controller = rememberStreamWebSocket("staff-events", app = "staff")
    LaunchedEffect(controller) {
        controller?.events?.collect { event ->
            staffEvent(event)?.let { staffEvent -> eventsBus.publish(staffEvent) }
        }
    }
}

/**
 * The [StaffEvent] a frame on the `staff-events` stream stands for, or null for
 * one with no topic. The socket opening again after being down is a
 * [StaffEvent.Unknown]: every list reads again, having missed whatever was
 * sent meanwhile.
 */
internal fun staffEvent(event: StreamWsEvent): StaffEvent? {
    if (event.type == REOPEN) return StaffEvent.Unknown(REOPEN)
    val topic = event.raw["topic"] as? String ?: return null
    return topic.toStaffEvent()
}

/**
 * Map a wire-format topic string to the matching [StaffEvent] variant.
 * Mirrors `TOPIC_ROUTES` in web's `use-staff-events.ts`.
 */
private fun String.toStaffEvent(): StaffEvent = when (this) {
    "staff/report" -> StaffEvent.NewReport
    "staff/moderation" -> StaffEvent.ModerationUpdated
    "staff/dispute" -> StaffEvent.NewDispute
    else -> StaffEvent.Unknown(this)
}
