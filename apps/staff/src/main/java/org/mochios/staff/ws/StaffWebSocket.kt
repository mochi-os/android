// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ws

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.mochios.android.ws.rememberStreamWebSocket

/**
 * Opens the `staff-events` WebSocket and republishes decoded `{topic, object}`
 * payloads to [eventsBus]. Mount once near the top of the nav graph - the
 * socket closes on dispose.
 */
@Composable
fun rememberStaffEventsSubscription(eventsBus: StaffEventsBus) {
    val controller = rememberStreamWebSocket("staff-events")
    LaunchedEffect(controller) {
        controller?.events?.collect { event ->
            val topic = event.raw["topic"] as? String ?: return@collect
            val staffEvent = topic.toStaffEvent()
            eventsBus.publish(staffEvent)
        }
    }
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
