// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ws

import org.mochios.android.model.WebSocketEvent
import org.mochios.android.websocket.REOPEN

/**
 * The key the Comptroller's staff events arrive on. Each list that follows
 * them subscribes for its view model's whole life, so the shared socket stays
 * open while any of them is on the back stack, a screen pushed over it
 * included.
 */
const val STAFF_EVENTS = "staff-events"

/**
 * Live staff event from the `staff-events` socket. Topics: `staff/report` ->
 * [NewReport], `staff/moderation` -> [ModerationUpdated], `staff/dispute` ->
 * [NewDispute], else [Unknown].
 */
sealed class StaffEvent {
    /** A new buyer-filed report landed in the reports queue. */
    object NewReport : StaffEvent()

    /**
     * A listing's moderation state changed, or an appeal was filed or decided.
     */
    object ModerationUpdated : StaffEvent()

    /** A new buyer dispute or chargeback landed. */
    object NewDispute : StaffEvent()

    /**
     * A topic with no explicit mapping, or the socket opening again after
     * missing events; treat as a hint to refetch.
     */
    data class Unknown(val topic: String) : StaffEvent()
}

/**
 * The [StaffEvent] a frame on the `staff-events` socket stands for, or null for
 * one with no topic. The socket opening again after being down is a
 * [StaffEvent.Unknown]: every list reads again, having missed whatever was
 * sent meanwhile.
 */
internal fun staffEvent(event: WebSocketEvent): StaffEvent? {
    if (event.type == REOPEN) return StaffEvent.Unknown(REOPEN)
    val topic = event.topic ?: return null
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
