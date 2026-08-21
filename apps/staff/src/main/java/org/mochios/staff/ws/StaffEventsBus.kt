// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ws

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live staff event from the `staff-events` WebSocket. Topics: `staff/report` ->
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
     * A topic with no explicit mapping; treat as a hint to refetch.
     */
    data class Unknown(val topic: String) : StaffEvent()
}

/**
 * Process-wide bus carrying [StaffEvent]s to ViewModels. `replay = 0`: a fresh
 * ViewModel pulls a fresh page, so late subscribers must not see history.
 */
@Singleton
class StaffEventsBus @Inject constructor() {
    private val _events = MutableSharedFlow<StaffEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )

    /** Hot flow of staff events. Subscribe with `events.collect { ... }`. */
    val events: SharedFlow<StaffEvent> = _events.asSharedFlow()

    /** Emit an event from the WebSocket subscription. Non-blocking. */
    fun publish(event: StaffEvent) {
        _events.tryEmit(event)
    }
}

