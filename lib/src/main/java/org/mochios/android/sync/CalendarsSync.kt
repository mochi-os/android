// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.mochios.android.account.MochiAccount

/**
 * The sync switch's view of calendar sync, and the per-calendar toggles under
 * it. [calendars] is every calendar the phone holds for the account, so the
 * drawer can show which are syncing without asking the server again.
 */
data class CalendarsSyncState(
    val enabled: Boolean = false,
    val permitted: Boolean = false,
    val running: Boolean = false,
    val time: Long = 0,
    val failure: SyncFailure = SyncFailure.NONE,
    val message: String? = null,
    val calendars: List<StoredCalendar> = emptyList(),
)

/**
 * Turns calendar sync on and off for the Mochi account and reports its state.
 * The adapter itself is [CalendarsSyncAdapter]; this is the switch's side.
 *
 * The account is syncable for calendars from the moment it is added, so the
 * phone's account settings list it; syncing starts only when the calendars
 * app's switch turns automatic sync on, after the permission is granted.
 */
object CalendarsSync {

    const val AUTHORITY = CalendarContract.AUTHORITY

    /** Requested at runtime from the switch, never at install. */
    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    /** The framework's floor for a periodic sync, in seconds. */
    private const val PERIOD = 15L * 60

    private const val PREFERENCES = "mochi_calendars_sync"
    private const val KEY_TIME = "time"
    private const val KEY_FAILURE = "failure"
    private const val KEY_MESSAGE = "message"
    private const val TAG = "CalendarsSync"

    fun permitted(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** The framework account for a Mochi identity, or null when none is registered. */
    fun account(context: Context, identity: String?): Account? {
        if (identity.isNullOrBlank()) return null
        if (MochiAccount.byIdentity(context, identity) == null) return null
        return Account(identity, MochiAccount.TYPE)
    }

    fun enabled(account: Account): Boolean =
        ContentResolver.getIsSyncable(account, AUTHORITY) > 0 &&
            ContentResolver.getSyncAutomatically(account, AUTHORITY)

    /** Turns automatic sync on, schedules the periodic sync and runs one now. */
    fun enable(account: Account) {
        ContentResolver.setIsSyncable(account, AUTHORITY, 1)
        ContentResolver.setSyncAutomatically(account, AUTHORITY, true)
        ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, PERIOD)
        request(account)
    }

    /**
     * Stops syncing, forgets the cursor and removes the adapter's calendars
     * and their events, so a later switch-on starts from a full download.
     * [logout] also takes the account out of sync altogether, as it is about
     * to be removed. Blocking: call it off the main thread.
     */
    fun disable(context: Context, account: Account, logout: Boolean = false) {
        ContentResolver.cancelSync(account, AUTHORITY)
        ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY)
        ContentResolver.setSyncAutomatically(account, AUTHORITY, false)
        if (logout) ContentResolver.setIsSyncable(account, AUTHORITY, 0)
        // The rows can only be removed while the permission is held; without
        // it the provider drops them itself once the account is gone.
        if (permitted(context)) {
            try {
                context.contentResolver.acquireContentProviderClient(AUTHORITY)?.use { provider ->
                    provider.delete(
                        Calendars.CONTENT_URI.adapter(account),
                        "${Calendars.ACCOUNT_NAME}=? and ${Calendars.ACCOUNT_TYPE}=?",
                        arrayOf(account.name, account.type),
                    )
                    ProviderCalendarsStore(provider, account).cursor(SyncCursor())
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not remove synced calendars", e)
            } catch (e: RemoteException) {
                Log.w(TAG, "Could not remove synced calendars", e)
            }
        }
        preferences(context).edit().clear().apply()
    }

    /**
     * Turns one calendar's sync on or off. Off removes its events from the
     * phone; on brings them back on the next run, which lists everything
     * again because the cursor no longer covers the calendar. Blocking.
     */
    fun calendar(context: Context, account: Account, local: Long, sync: Boolean) {
        if (!permitted(context)) return
        try {
            context.contentResolver.acquireContentProviderClient(AUTHORITY)?.use { provider ->
                provider.update(
                    Calendars.CONTENT_URI.adapter(account),
                    ContentValues().apply {
                        put(Calendars.SYNC_EVENTS, if (sync) 1 else 0)
                        put(Calendars.VISIBLE, if (sync) 1 else 0)
                    },
                    "${Calendars._ID}=?",
                    arrayOf(local.toString()),
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not change calendar sync", e)
        } catch (e: RemoteException) {
            Log.w(TAG, "Could not change calendar sync", e)
        }
        request(account)
    }

    /** A sync now, ahead of the schedule and past any backoff. */
    fun request(account: Account) {
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(account, AUTHORITY, extras)
    }

    /** A sync when the app comes to the foreground, if the switch is on. */
    fun foreground(context: Context, identity: String?) {
        val account = account(context, identity) ?: return
        if (!enabled(account)) return
        ContentResolver.requestSync(account, AUTHORITY, Bundle.EMPTY)
    }

    /** The switch's state, refreshed as the framework and the adapter report. */
    fun state(context: Context, account: Account?): Flow<CalendarsSyncState> = callbackFlow {
        val preferences = preferences(context)
        val emit = { trySend(snapshot(context, preferences, account)) }
        emit()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> emit() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        val handle = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
        ) { emit() }
        awaitClose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            ContentResolver.removeStatusChangeListener(handle)
        }
    }

    private fun snapshot(context: Context, preferences: SharedPreferences, account: Account?): CalendarsSyncState =
        CalendarsSyncState(
            enabled = account != null && enabled(account),
            permitted = permitted(context),
            running = account != null && ContentResolver.isSyncActive(account, AUTHORITY),
            time = preferences.getLong(KEY_TIME, 0),
            failure = preferences.getString(KEY_FAILURE, null)
                ?.let { name -> SyncFailure.entries.firstOrNull { it.name == name } }
                ?: SyncFailure.NONE,
            message = preferences.getString(KEY_MESSAGE, null),
            calendars = if (account == null) emptyList() else calendars(context, account),
        )

    /** The account's calendars as the provider holds them, empty without the permission. */
    fun calendars(context: Context, account: Account): List<StoredCalendar> {
        if (!permitted(context)) return emptyList()
        return try {
            context.contentResolver.acquireContentProviderClient(AUTHORITY)?.use { provider ->
                ProviderCalendarsStore(provider, account).calendars()
            }.orEmpty()
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not read synced calendars", e)
            emptyList()
        } catch (e: RemoteException) {
            Log.w(TAG, "Could not read synced calendars", e)
            emptyList()
        }
    }

    // ---- the adapter's reports ----

    /**
     * How a sync ended. A run that completed moves the last-sync time on,
     * even with an event refused; one that failed keeps the time it had.
     */
    internal fun record(context: Context, failure: SyncFailure, message: String? = null) {
        preferences(context).edit().apply {
            if (failure == SyncFailure.NONE || failure == SyncFailure.REFUSED) {
                putLong(KEY_TIME, System.currentTimeMillis() / 1000)
            }
            putString(KEY_FAILURE, failure.name)
            if (message == null) remove(KEY_MESSAGE) else putString(KEY_MESSAGE, message)
        }.apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
