// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.accounts.Account
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.mochios.android.auth.SessionManager
import java.io.IOException

/** What the adapter needs from the graph: the transport, and who is signed in. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContactsSyncEntryPoint {
    fun source(): ContactsSource
    fun sessionManager(): SessionManager
}

/**
 * The framework's entry to contacts sync. Runs [ContactsSyncCycle] for the
 * signed-in account against the contacts provider and reports the result to
 * the framework, which backs off on a soft error and stops on a hard one,
 * and to [ContactsSync] for the switch's status line.
 */
class ContactsSyncAdapter(context: Context) : AbstractThreadedSyncAdapter(context, true) {

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        result: SyncResult,
    ) {
        if (extras.getBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE, false)) {
            // A freshly added account: syncable, so the phone's account
            // settings list it, and off until the people app's switch turns
            // it on after the permission is granted.
            ContentResolver.setIsSyncable(account, authority, 1)
            ContentResolver.setSyncAutomatically(account, authority, false)
            return
        }
        // A sync queued before the switch went off would download everything
        // the switch has just removed.
        if (!ContactsSync.enabled(account)) return
        val deps = EntryPointAccessors.fromApplication(context, ContactsSyncEntryPoint::class.java)
        val bound = runBlocking { deps.sessionManager().getBoundIdentity() }
        if (bound != account.name) {
            // The transport speaks for the bound account only; another
            // account's rows are not this session's to write.
            return
        }
        if (!ContactsSync.permitted(context)) {
            ContactsSync.record(context, SyncFailure.PERMISSION)
            result.stats.numAuthExceptions++
            return
        }
        try {
            val store = ProviderContactsStore(provider, account)
            store.prepare()
            val outcome = runBlocking { ContactsSyncCycle(deps.source(), store).run() }
            result.stats.numInserts += outcome.downloaded.toLong()
            result.stats.numUpdates += outcome.uploaded.toLong()
            result.stats.numDeletes += outcome.removed.toLong()
            result.stats.numSkippedEntries += outcome.failures.size.toLong()
            if (outcome.failures.isEmpty()) {
                ContactsSync.record(context, SyncFailure.NONE)
            } else {
                ContactsSync.record(context, SyncFailure.REFUSED, outcome.failures.first())
            }
        } catch (_: SyncAuthorization) {
            result.stats.numAuthExceptions++
            ContactsSync.record(context, SyncFailure.AUTHORIZATION)
        } catch (e: SyncRefused) {
            // The server refused the change listing or a fetch: retrying
            // later is all there is to do, and the message says why.
            result.stats.numIoExceptions++
            ContactsSync.record(context, SyncFailure.REFUSED, e.message)
        } catch (_: IOException) {
            result.stats.numIoExceptions++
            ContactsSync.record(context, SyncFailure.TRANSPORT)
        } catch (e: Exception) {
            // A provider failure or a bug: soft, so the framework tries again
            // later rather than giving up on the account.
            Log.e(TAG, "Contacts sync failed", e)
            result.stats.numIoExceptions++
            ContactsSync.record(context, SyncFailure.TRANSPORT)
        }
    }

    private companion object {
        const val TAG = "ContactsSync"
    }
}

/**
 * Binds [ContactsSyncAdapter] for the framework. One adapter per process, as
 * the framework requires; the lock covers the first bind racing a second.
 */
class ContactsSyncService : Service() {

    override fun onCreate() {
        super.onCreate()
        synchronized(LOCK) {
            if (adapter == null) adapter = ContactsSyncAdapter(applicationContext)
        }
    }

    override fun onBind(intent: Intent): IBinder? = adapter?.syncAdapterBinder

    private companion object {
        val LOCK = Any()
        var adapter: ContactsSyncAdapter? = null
    }
}
