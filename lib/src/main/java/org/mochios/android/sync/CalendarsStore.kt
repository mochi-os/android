// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

/**
 * One `Events` row of the account, with the `Reminders` rows hanging off it.
 * [values] is keyed by column name, so the same shape describes a row the
 * mapping builds and a row read back from the provider. [deleted] is the
 * provider's own flag, which a cancelled occurrence of a recurring event
 * carries and the upload turns into an `EXDATE`.
 */
class EventRow(
    val values: Map<String, Any?>,
    val reminders: List<Int> = emptyList(),
    val deleted: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is EventRow && values == other.values && reminders == other.reminders && deleted == other.deleted

    override fun hashCode(): Int = 31 * (31 * values.hashCode() + reminders.hashCode()) + deleted.hashCode()

    override fun toString(): String = "EventRow($values, reminders=$reminders, deleted=$deleted)"
}

/**
 * A calendar of the account as the provider holds it. [local] is the
 * provider's row id and [remote] the server's calendar id (`_SYNC_ID`).
 * [sync] is the per-calendar toggle (`SYNC_EVENTS`), which is "sync this
 * calendar to the phone" and separate from the checkbox that shows it in the
 * app's own views.
 */
data class StoredCalendar(
    val local: Long,
    val remote: String,
    val name: String = "",
    val colour: String = "",
    val readonly: Boolean = false,
    val sync: Boolean = true,
)

/**
 * One provider row of an event: the master, or one override of it. [local] is
 * the provider's row id; [remote] the server's event id, which every row of
 * one event shares, null for an event made on the phone and not yet uploaded.
 * [name] is the row's own `_SYNC_ID`, [etag] the server's, and [original] the
 * master's `_SYNC_ID` on an override, null on a master. An override of an
 * event made on the phone has no `_SYNC_ID` to point at yet, so the phone's
 * calendar app names the master's row id in `ORIGINAL_ID`, which [origin]
 * carries. [slug] is the name the
 * phone gave an event it made, carried in `SYNC_DATA3`: null until its first
 * create, then sent with every create of the row.
 */
data class StoredEvent(
    val local: Long,
    val calendar: Long,
    val remote: String?,
    val name: String? = null,
    val etag: String? = null,
    val original: String? = null,
    val origin: Long = 0,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val slug: String? = null,
) {
    /** Whether this row overrides one occurrence of a recurring event. */
    fun exception(): Boolean = original != null || origin != 0L
}

/**
 * Where a sync got to: the server cursor of the last completed download, and
 * the calendars that download covered. A calendar switched on later is not in
 * the set, so the next run lists everything again rather than leaving the
 * calendar empty until one of its events happens to change.
 */
data class SyncCursor(
    val version: Long = 0,
    val calendars: Set<String> = emptySet(),
)

/**
 * The phone side of calendar sync: the account's rows in the calendar
 * provider, and the sync state that carries the server cursor. Every write is
 * made as the sync adapter, so nothing here marks a row dirty.
 */
interface CalendarsStore {

    /** Where the last completed download got to. */
    fun cursor(): SyncCursor

    fun cursor(value: SyncCursor)

    /** Every calendar of the account. */
    fun calendars(): List<StoredCalendar>

    /**
     * Writes the server's calendar, creating the provider's row or bringing
     * its name, colour and access level up to date, and answers its row id.
     * The per-calendar sync toggle is the user's and is never overwritten.
     */
    fun calendar(calendar: SyncedCalendar): Long

    /** Removes a calendar and every event in it. */
    fun remove(calendar: StoredCalendar)

    /** Every event row of the account, deleted ones included. */
    fun events(): List<StoredEvent>

    /** The provider rows for one event, master first. */
    fun rows(group: List<StoredEvent>): List<EventRow>

    /**
     * Writes an event's rows into [calendar], removing [replacing] first, so
     * an event whose overrides changed does not keep the ones it lost. An
     * empty [replacing] is a plain insert.
     */
    fun write(calendar: Long, event: SyncedEvent, replacing: List<StoredEvent> = emptyList())

    /**
     * Stores the server's id and etag on the phone's rows after an upload.
     * The dirty flags clear only when [settled] says the rows are still the
     * ones that went up: an edit made on the phone during the upload leaves
     * them dirty, so it goes up next time rather than being taken for
     * uploaded.
     */
    fun bind(group: List<StoredEvent>, event: SyncedEvent, settled: Boolean)

    /**
     * Names an event made on the phone, in `SYNC_DATA3`, before its first
     * create goes out. Every create of the event carries the name, so one
     * sent again finds the event the first one made.
     */
    fun slug(local: Long, value: String)

    /** Removes the event's rows for good. */
    fun remove(group: List<StoredEvent>)
}
