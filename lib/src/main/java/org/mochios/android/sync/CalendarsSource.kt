// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

/**
 * A calendar as the server holds it. [kind] is `own`, `subscription` or
 * `birthdays`; the last two are read-only, as is a calendar shared with the
 * user, which [readonly] reports on its own.
 */
data class SyncedCalendar(
    val id: String,
    val name: String,
    val colour: String = "",
    val kind: String = "own",
    val readonly: Boolean = false,
    val default: Boolean = false,
)

/**
 * An event as the server holds it: its id, calendar, etag and component tree.
 * The tree is the master `VEVENT` and any overrides beside it, each an own
 * `VEVENT` carrying a `RECURRENCE-ID`. [slug] is its name within the calendar,
 * the one a create gave it or else its id; null from a server that does not
 * say, which matches no row.
 */
data class SyncedEvent(
    val id: String,
    val calendar: String,
    val etag: String,
    val components: List<EventComponent>,
    val slug: String? = null,
)

/**
 * What changed since a cursor: [version] is the next cursor, [changed] the
 * ids to fetch and [deleted] the ids to drop. When [reset] is set, [changed]
 * is every event the server holds and a row outside it is gone: the answer to
 * a cursor of 0, or to one older than the deletions the server remembers.
 */
data class CalendarsChanges(
    val version: Long,
    val changed: List<String>,
    val deleted: List<String>,
    val reset: Boolean = false,
)

/**
 * The server side of calendar sync: the calendars app's JSON actions, called
 * with the app token the client already mints. The adapter never speaks
 * iCalendar or CalDAV; the component tree is the wire format both ways.
 *
 * Implementations translate HTTP status into the exceptions [ContactsSource]
 * declares — [SyncConflict] for 412, [SyncMissing] for 404, [SyncAuthorization]
 * for 401 and 403, [SyncRefused] for any other client error — and let
 * transport failures (an [java.io.IOException]) propagate as they are.
 */
interface CalendarsSource {

    /** Every calendar of the identity, the read-only ones included. */
    suspend fun calendars(): List<SyncedCalendar>

    /** Changes since [since], 0 for every event. */
    suspend fun changes(since: Long): CalendarsChanges

    /** The full events for up to [BATCH] ids; ids the server lacks are absent. */
    suspend fun fetch(ids: List<String>): List<SyncedEvent>

    /**
     * Creates an event from [components] in [calendar], named [slug]. When the
     * calendar already holds an event of that name the server answers it, as
     * it holds it, instead of making a second.
     */
    suspend fun create(calendar: String, components: List<EventComponent>, slug: String): SyncedEvent

    /**
     * Replaces the event's components, and moves it to [calendar] when that
     * differs from the one it is in. Refused with [SyncConflict] on a stale
     * [etag].
     */
    suspend fun update(id: String, etag: String, calendar: String?, components: List<EventComponent>): SyncedEvent

    /** Deletes the event, refused with [SyncConflict] on a stale [etag]. */
    suspend fun delete(id: String, etag: String)

    companion object {
        /** Most ids one [fetch] may carry. */
        const val BATCH = 500
    }
}
