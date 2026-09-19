// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.Manifest
import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.mochios.android.account.MochiAccount

/** Why the last sync did not complete, for the status line under the switch. */
enum class SyncFailure {
    NONE,

    /** The contacts permission was withdrawn after the switch went on. */
    PERMISSION,

    /** The session or app token is no longer accepted. */
    AUTHORIZATION,

    /** The server could not be reached; the framework retries. */
    TRANSPORT,

    /** The server refused a contact; [ContactsSyncState.message] says why. */
    REFUSED,
}

/**
 * The sync switch's view of the adapter: whether it is on, whether the
 * contacts permission is held, whether a sync is running, and how the last
 * one ended. [time] is the last completed sync in epoch seconds, 0 for none.
 */
data class ContactsSyncState(
    val enabled: Boolean = false,
    val permitted: Boolean = false,
    val running: Boolean = false,
    val time: Long = 0,
    val failure: SyncFailure = SyncFailure.NONE,
    val message: String? = null,
)

/**
 * Turns contacts sync on and off for the Mochi account and reports its state.
 * The adapter itself is [ContactsSyncAdapter]; this is the switch's side.
 *
 * The account is syncable for contacts from the moment it is added, so the
 * phone's account settings list it; syncing starts only when the people
 * app's switch turns automatic sync on, after the permission is granted.
 */
object ContactsSync {

    const val AUTHORITY = ContactsContract.AUTHORITY

    /** Requested at runtime from the switch, never at install. */
    val PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    /** The framework's floor for a periodic sync, in seconds. */
    private const val PERIOD = 15L * 60

    private const val PREFERENCES = "mochi_contacts_sync"
    private const val KEY_TIME = "time"
    private const val KEY_FAILURE = "failure"
    private const val KEY_MESSAGE = "message"
    private const val TAG = "ContactsSync"

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
     * Stops syncing, forgets the cursor and removes the adapter's rows, so a
     * later switch-on starts from a full download. [logout] also takes the
     * account out of sync altogether, as it is about to be removed. Blocking:
     * call it off the main thread.
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
                        RawContacts.CONTENT_URI.adapter(),
                        "${RawContacts.ACCOUNT_NAME}=? and ${RawContacts.ACCOUNT_TYPE}=?",
                        arrayOf(account.name, account.type),
                    )
                    ProviderContactsStore(provider, account).version(0)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not remove synced contacts", e)
            } catch (e: RemoteException) {
                Log.w(TAG, "Could not remove synced contacts", e)
            }
        }
        preferences(context).edit().clear().apply()
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
    fun state(context: Context, account: Account?): Flow<ContactsSyncState> = callbackFlow {
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

    private fun snapshot(context: Context, preferences: SharedPreferences, account: Account?): ContactsSyncState =
        ContactsSyncState(
            enabled = account != null && enabled(account),
            permitted = permitted(context),
            running = account != null && ContentResolver.isSyncActive(account, AUTHORITY),
            time = preferences.getLong(KEY_TIME, 0),
            failure = preferences.getString(KEY_FAILURE, null)
                ?.let { name -> SyncFailure.entries.firstOrNull { it.name == name } }
                ?: SyncFailure.NONE,
            message = preferences.getString(KEY_MESSAGE, null),
        )

    // ---- the adapter's reports ----

    /**
     * How a sync ended. A run that completed moves the last-sync time on,
     * even with a contact refused; one that failed keeps the time it had.
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
