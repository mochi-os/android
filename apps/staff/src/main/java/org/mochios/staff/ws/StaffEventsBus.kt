// Copyright © 2026 Mochi OÜ
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
 * Live staff event surfaced from the staff app's `staff-events` WebSocket.
 *
 * Mirrors the topic strings the Comptroller fans out through
 * `event_message_notify` in `apps/staff/staff.star` (and the topic mapping
 * used by web's `useStaffEvents()` hook):
 *
 *  - `staff/report`     -> [NewReport]
 *  - `staff/moderation` -> [ModerationUpdated]
 *  - `staff/dispute`    -> [NewDispute]
 *
 * Unknown / future topics surface as [Unknown] so callers can choose to
 * refresh everything for safety.
 */
sealed class StaffEvent {
    /** A new buyer-filed report landed in the reports queue. */
    object NewReport : StaffEvent()

    /**
     * Moderation queue changed — a listing flipped between
     * review/hold/approved/rejected/removed, or an appeal was filed or
     * decided. Affects the listings, appeals, and moderation log screens.
     */
    object ModerationUpdated : StaffEvent()

    /** A new buyer dispute or chargeback landed. */
    object NewDispute : StaffEvent()

    /**
     * Topic we don't have an explicit mapping for. Subscribers that want
     * to be safe can treat this as a hint to refetch their list; most can
     * ignore it.
     */
    data class Unknown(val topic: String) : StaffEvent()
}

/**
 * Application-wide bus carrying [StaffEvent]s from the staff-events
 * WebSocket to interested ViewModels. Single instance per process; each
 * ViewModel collects [events] and decides which `StaffEvent` variants
 * cause a refresh.
 *
 * Buffer = 16 so a slow consumer doesn't stall the producer. `replay = 0`
 * because late subscribers shouldn't see the historical event stream — a
 * fresh ViewModel will pull a fresh page from the server anyway.
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

// TODO: When ReportsViewModel, AppealsViewModel, DisputesViewModel, and
// ModerationLogViewModel land (being built concurrently by parallel
// agents), inject StaffEventsBus and add a `viewModelScope.launch {
// eventsBus.events.collect { ... } }` that calls `reload()` on the
// matching variant. ListingsViewModel wiring is already in this branch
// (refreshes on ModerationUpdated).
