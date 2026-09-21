// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.provider.SyncStateContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * [CalendarsStore] over the calendar provider, for one account. Every URI
 * carries [CalendarContract.CALLER_IS_SYNCADAPTER] and the account, so the
 * provider neither marks these writes dirty nor hides deleted rows from the
 * queries.
 */
class ProviderCalendarsStore(
    private val provider: ContentProviderClient,
    private val account: Account,
) : CalendarsStore {

    override fun cursor(): SyncCursor {
        val raw = SyncStateContract.Helpers
            .get(provider, CalendarContract.SyncState.CONTENT_URI.adapter(account), account)
            ?.toString(Charsets.UTF_8)
            ?.trim()
            .orEmpty()
        if (raw.isEmpty()) return SyncCursor()
        return try {
            val state = JSONObject(raw)
            val calendars = state.optJSONArray(CALENDARS) ?: JSONArray()
            SyncCursor(
                version = state.optLong(VERSION),
                calendars = (0 until calendars.length()).mapNotNull { calendars.optString(it) }.toSet(),
            )
        } catch (_: org.json.JSONException) {
            SyncCursor()
        }
    }

    override fun cursor(value: SyncCursor) {
        val state = JSONObject()
            .put(VERSION, value.version)
            .put(CALENDARS, JSONArray(value.calendars.toList()))
        SyncStateContract.Helpers.set(
            provider,
            CalendarContract.SyncState.CONTENT_URI.adapter(account),
            account,
            state.toString().toByteArray(Charsets.UTF_8),
        )
    }

    override fun calendars(): List<StoredCalendar> {
        val out = mutableListOf<StoredCalendar>()
        provider.query(
            Calendars.CONTENT_URI.adapter(account),
            arrayOf(
                Calendars._ID,
                Calendars._SYNC_ID,
                Calendars.CALENDAR_DISPLAY_NAME,
                Calendars.CALENDAR_COLOR,
                Calendars.CALENDAR_ACCESS_LEVEL,
                Calendars.SYNC_EVENTS,
            ),
            "${Calendars.ACCOUNT_NAME}=? and ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(account.name, account.type),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val remote = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                out.add(
                    StoredCalendar(
                        local = cursor.getLong(0),
                        remote = remote,
                        name = cursor.getString(2).orEmpty(),
                        colour = paint(cursor.getInt(3)),
                        readonly = cursor.getInt(4) < Calendars.CAL_ACCESS_CONTRIBUTOR,
                        sync = cursor.getInt(5) != 0,
                    ),
                )
            }
        }
        return out
    }

    override fun calendar(calendar: SyncedCalendar): Long {
        val existing = calendars().firstOrNull { it.remote == calendar.id }
        val values = ContentValues().apply {
            put(Calendars.CALENDAR_DISPLAY_NAME, calendar.name)
            put(Calendars.NAME, calendar.name)
            put(Calendars.CALENDAR_COLOR, colour(calendar.colour))
            put(
                Calendars.CALENDAR_ACCESS_LEVEL,
                if (calendar.readonly) Calendars.CAL_ACCESS_READ else Calendars.CAL_ACCESS_OWNER,
            )
        }
        if (existing != null) {
            provider.update(
                Calendars.CONTENT_URI.adapter(account),
                values,
                "${Calendars._ID}=?",
                arrayOf(existing.local.toString()),
            )
            return existing.local
        }
        values.apply {
            put(Calendars.ACCOUNT_NAME, account.name)
            put(Calendars.ACCOUNT_TYPE, account.type)
            put(Calendars.OWNER_ACCOUNT, account.name)
            put(Calendars._SYNC_ID, calendar.id)
            put(Calendars.CALENDAR_TIME_ZONE, java.util.TimeZone.getDefault().id)
            // On by default, as a calendar the user keeps on the server is
            // one they want on the phone; both are theirs to change after.
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.VISIBLE, 1)
        }
        val uri = provider.insert(Calendars.CONTENT_URI.adapter(account), values)
        return uri?.lastPathSegment?.toLongOrNull() ?: 0
    }

    override fun remove(calendar: StoredCalendar) {
        provider.delete(
            Calendars.CONTENT_URI.adapter(account),
            "${Calendars._ID}=?",
            arrayOf(calendar.local.toString()),
        )
    }

    override fun events(): List<StoredEvent> {
        val out = mutableListOf<StoredEvent>()
        val synced = calendars().filter { it.sync }.map { it.local }
        if (synced.isEmpty()) return out
        provider.query(
            Events.CONTENT_URI.adapter(account),
            arrayOf(
                Events._ID,
                Events.CALENDAR_ID,
                Events.SYNC_DATA2,
                Events._SYNC_ID,
                Events.SYNC_DATA1,
                Events.ORIGINAL_SYNC_ID,
                Events.ORIGINAL_ID,
                Events.DIRTY,
                Events.DELETED,
                Events.SYNC_DATA3,
            ),
            "${Events.CALENDAR_ID} in (${synced.joinToString(",") { "?" }})",
            synced.map { it.toString() }.toTypedArray(),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    StoredEvent(
                        local = cursor.getLong(0),
                        calendar = cursor.getLong(1),
                        remote = cursor.getString(2)?.takeIf { it.isNotBlank() }
                            ?: cursor.getString(3)?.takeIf { it.isNotBlank() }?.substringBefore('#'),
                        name = cursor.getString(3),
                        etag = cursor.getString(4),
                        original = cursor.getString(5)?.takeIf { it.isNotBlank() }?.substringBefore('#'),
                        origin = cursor.getLong(6),
                        dirty = cursor.getInt(7) != 0,
                        deleted = cursor.getInt(8) != 0,
                        slug = cursor.getString(9)?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return out
    }

    override fun rows(group: List<StoredEvent>): List<EventRow> =
        group.mapNotNull { stored -> read(stored) }

    /** One provider row with its reminders, or null when it has gone. */
    private fun read(stored: StoredEvent): EventRow? {
        var row: EventRow? = null
        provider.query(
            Events.CONTENT_URI.adapter(account),
            CalendarsMapping.COLUMNS,
            "${Events._ID}=?",
            arrayOf(stored.local.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val values = mutableMapOf<String, Any?>()
            for ((index, column) in CalendarsMapping.COLUMNS.withIndex()) {
                values[column] = if (cursor.isNull(index)) {
                    null
                } else {
                    when (column) {
                        Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION, Events.DURATION,
                        Events.EVENT_TIMEZONE, Events.EVENT_END_TIMEZONE, Events.RRULE, Events.RDATE,
                        Events.EXDATE, Events._SYNC_ID, Events.SYNC_DATA1, Events.SYNC_DATA2,
                        Events.SYNC_DATA3, Events.ORIGINAL_SYNC_ID,
                        -> cursor.getString(index)
                        else -> cursor.getLong(index)
                    }
                }
            }
            row = EventRow(values, reminders(stored.local), stored.deleted)
        }
        return row
    }

    /** The minutes before the start the event's reminders fire. */
    private fun reminders(local: Long): List<Int> {
        val out = mutableListOf<Int>()
        provider.query(
            Reminders.CONTENT_URI.adapter(account),
            arrayOf(Reminders.MINUTES, Reminders.METHOD),
            "${Reminders.EVENT_ID}=?",
            arrayOf(local.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getInt(1) == Reminders.METHOD_SMS) continue
                out.add(cursor.getInt(0))
            }
        }
        return out
    }

    override fun write(calendar: Long, event: SyncedEvent, replacing: List<StoredEvent>) {
        val batch = arrayListOf<ContentProviderOperation>()
        for (stored in replacing) {
            batch.add(
                ContentProviderOperation.newDelete(Events.CONTENT_URI.adapter(account))
                    .withSelection("${Events._ID}=?", arrayOf(stored.local.toString()))
                    .build(),
            )
        }
        for (row in CalendarsMapping.rows(event, calendar)) {
            val index = batch.size
            batch.add(
                ContentProviderOperation.newInsert(Events.CONTENT_URI.adapter(account))
                    .withValues(row.values.toContentValues())
                    .build(),
            )
            for (minutes in row.reminders) {
                batch.add(
                    ContentProviderOperation.newInsert(Reminders.CONTENT_URI.adapter(account))
                        .withValueBackReference(Reminders.EVENT_ID, index)
                        .withValue(Reminders.MINUTES, minutes)
                        .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                        .build(),
                )
            }
        }
        if (batch.isNotEmpty()) provider.applyBatch(batch)
    }

    override fun bind(group: List<StoredEvent>, event: SyncedEvent, settled: Boolean) {
        val batch = arrayListOf<ContentProviderOperation>()
        for (stored in group) {
            val values = ContentValues().apply {
                put(Events.SYNC_DATA1, event.etag)
                put(Events.SYNC_DATA2, event.id)
                put(Events.SYNC_DATA3, event.slug)
                if (stored.exception()) {
                    put(Events.ORIGINAL_SYNC_ID, event.id)
                } else {
                    put(Events._SYNC_ID, event.id)
                }
                if (settled) put(Events.DIRTY, 0)
            }
            batch.add(
                ContentProviderOperation.newUpdate(Events.CONTENT_URI.adapter(account))
                    .withSelection("${Events._ID}=?", arrayOf(stored.local.toString()))
                    .withValues(values)
                    .build(),
            )
        }
        if (batch.isNotEmpty()) provider.applyBatch(batch)
    }

    override fun slug(local: Long, value: String) {
        provider.update(
            Events.CONTENT_URI.adapter(account),
            ContentValues().apply { put(Events.SYNC_DATA3, value) },
            "${Events._ID}=?",
            arrayOf(local.toString()),
        )
    }

    override fun remove(group: List<StoredEvent>) {
        for (stored in group) {
            provider.delete(
                Events.CONTENT_URI.adapter(account),
                "${Events._ID}=?",
                arrayOf(stored.local.toString()),
            )
        }
    }

    /** The provider row for one event of the account, for the module's own reads. */
    fun uri(local: Long): Uri = ContentUris.withAppendedId(Events.CONTENT_URI, local)

    private fun Map<String, Any?>.toContentValues(): ContentValues = ContentValues().apply {
        for ((column, value) in this@toContentValues) {
            when (value) {
                null -> putNull(column)
                is Long -> put(column, value)
                is Int -> put(column, value)
                is String -> put(column, value)
                else -> put(column, value.toString())
            }
        }
    }

    /** `#rrggbb` as the provider's packed integer, the Mochi blue when it will not parse. */
    private fun colour(value: String): Int = try {
        Color.parseColor(value.ifBlank { DEFAULT })
    } catch (_: IllegalArgumentException) {
        Color.parseColor(DEFAULT)
    }

    /** The provider's packed integer back as `#rrggbb`. */
    private fun paint(value: Int): String = String.format("#%06x", value and 0xffffff)

    private companion object {
        const val VERSION = "version"
        const val CALENDARS = "calendars"

        /** The colour the calendars app gives a calendar with none. */
        const val DEFAULT = "#60a5fa"
    }
}

/** The URI as the sync adapter for [account]: writes stay clean and deleted rows are visible. */
fun Uri.adapter(account: Account): Uri = buildUpon()
    .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
    .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
    .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
    .build()
