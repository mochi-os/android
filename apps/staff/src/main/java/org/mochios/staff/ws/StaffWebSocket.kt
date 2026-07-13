// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ws

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.mochios.android.ws.rememberGameWebSocket

/**
 * Compose-level wrapper around lib's [rememberGameWebSocket] that opens
 * the `staff-events` WebSocket and forwards decoded payloads to
 * [eventsBus] as typed [StaffEvent]s.
 *
 * Mirrors web's `useStaffEvents()` hook: subscribes to
 * `wss://server/_/websocket?key=staff-events`, then routes incoming
 * `{topic, object}` payloads to the matching React Query invalidations
 * (here: ViewModel refreshes via the shared [StaffEventsBus]).
 *
 * Lifecycle: the lib helper closes the socket on dispose, so dropping the
 * Composable that owns this wrapper unwinds the connection. Mount it once
 * near the top of the staff app's nav graph (e.g. the StaffLayout shell)
 * so a single socket survives across screen changes.
 *
 * Payload shape: the server writes
 * `mochi.websocket.write("staff-events", {"topic": ..., "object": ...})`,
 * which arrives as a JSON map. Lib's [rememberGameWebSocket] decodes it
 * into a `GameWsEvent` whose `raw` map carries the full payload — we read
 * `topic` from there and map to the matching [StaffEvent].
 */
@Composable
fun rememberStaffEventsSubscription(eventsBus: StaffEventsBus) {
    val controller = rememberGameWebSocket("staff-events")
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
