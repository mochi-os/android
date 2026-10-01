// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.ContentResolver
import android.content.Context
import android.content.PeriodicSync
import android.os.Bundle
import java.net.URI
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Cross-app session sharing via AccountManager: every Mochi app registers
 * account type [TYPE], and the same signing key is the only access guard.
 * [Account.name] is what the phone shows for the account, in its calendar and
 * contacts apps and its account settings: the identity's email address, else
 * its name and the server it signs in to. The identity entity ID is kept in the
 * account's own data; an account written before that was named after it, and
 * is renamed on the next sign-in. Accounts on different servers coexist.
 */
object MochiAccount {

    /** Single account type for all Mochi accounts. */
    const val TYPE = "org.mochios.account"

    /** Auth-token type for the raw session cookie. */
    const val TOKEN_SESSION = "session"

    private const val USER_DATA_IDENTITY = "identity"
    private const val USER_DATA_SERVER = "server"
    private const val USER_DATA_NAME = "name"
    private const val USER_DATA_FINGERPRINT = "fingerprint"

    data class Snapshot(
        val identity: String,
        val name: String,
        val server: String,
        val fingerprint: String?,
        val session: String
    )

    fun upsert(
        context: Context,
        identity: String,
        name: String,
        email: String,
        server: String,
        fingerprint: String?,
        session: String
    ) {
        if (identity.isBlank()) return
        val am = AccountManager.get(context)
        val existing = find(am, identity)
        val taken = am.getAccountsByType(TYPE).filter { it != existing }.map { it.name }.toSet()
        val wanted = unique(label(email, name, server, identity), host(server), taken)
        if (existing == null) {
            val data = Bundle().apply {
                putString(USER_DATA_IDENTITY, identity)
                putString(USER_DATA_NAME, name)
                putString(USER_DATA_SERVER, server)
                if (fingerprint != null) putString(USER_DATA_FINGERPRINT, fingerprint)
            }
            am.addAccountExplicitly(Account(wanted, TYPE), session, data)
            return
        }
        am.setPassword(existing, session)
        am.setUserData(existing, USER_DATA_IDENTITY, identity)
        am.setUserData(existing, USER_DATA_NAME, name)
        am.setUserData(existing, USER_DATA_SERVER, server)
        if (fingerprint != null) am.setUserData(existing, USER_DATA_FINGERPRINT, fingerprint)
        if (existing.name != wanted) rename(am, existing, wanted)
    }

    /**
     * What the phone shows for an account: its email address, as a phone
     * shows any other account; else the name with the server's host, since
     * the same name can sign in to two servers; else the host, and only as a
     * last resort the identity itself.
     */
    fun label(email: String, name: String, server: String, identity: String): String {
        val address = email.trim()
        if (address.isNotEmpty()) return address
        val host = host(server)
        val person = name.trim()
        return when {
            person.isNotEmpty() && host.isNotEmpty() -> "$person ($host)"
            person.isNotEmpty() -> person
            host.isNotEmpty() -> host
            else -> identity
        }
    }

    /**
     * [wanted], or where another account already has that name - the same
     * email address on a second server - the name with the host, then a
     * number.
     */
    fun unique(wanted: String, host: String, taken: Set<String>): String {
        if (wanted !in taken) return wanted
        val hosted = if (host.isNotEmpty() && !wanted.endsWith("($host)")) "$wanted ($host)" else wanted
        if (hosted !in taken) return hosted
        var number = 2
        while ("$hosted $number" in taken) number++
        return "$hosted $number"
    }

    /** The host the app signs in to, from the server address it stores. */
    fun host(server: String): String {
        val trimmed = server.trim()
        val parsed = runCatching { URI(trimmed).host }.getOrNull()
        if (!parsed.isNullOrBlank()) return parsed
        return trimmed.substringAfter("://").substringBefore('/').substringBefore(':')
    }

    /** The framework account for an identity, or null when none is registered. */
    fun account(context: Context, identity: String?): Account? {
        if (identity.isNullOrBlank()) return null
        return try {
            find(AccountManager.get(context), identity)
        } catch (_: SecurityException) {
            null
        }
    }

    /** The identity entity ID a framework account belongs to. */
    fun identityOf(context: Context, account: Account): String =
        try {
            identityOf(AccountManager.get(context), account)
        } catch (_: SecurityException) {
            account.name
        }

    private fun identityOf(am: AccountManager, account: Account): String =
        am.getUserData(account, USER_DATA_IDENTITY)?.takeIf { it.isNotBlank() } ?: account.name

    private fun find(am: AccountManager, identity: String): Account? =
        am.getAccountsByType(TYPE).firstOrNull { identityOf(am, it) == identity }

    /**
     * Renames an account, carrying its sync settings across: the framework
     * treats a rename as a removal and an addition, so the phone's calendar
     * and contacts stores drop the old name's rows and the next sync
     * downloads them again under the new one.
     */
    private fun rename(am: AccountManager, account: Account, name: String) {
        val authorities = ContentResolver.getSyncAdapterTypes()
            .filter { it.accountType == TYPE }
            .map { it.authority }
        val settings = authorities.map { authority ->
            Triple(
                authority,
                ContentResolver.getIsSyncable(account, authority),
                ContentResolver.getSyncAutomatically(account, authority),
            ) to ContentResolver.getPeriodicSyncs(account, authority).toList()
        }
        am.renameAccount(account, name, { future ->
            val renamed = runCatching { future.result }.getOrNull() ?: return@renameAccount
            for ((setting, periodic) in settings) {
                val (authority, syncable, automatic) = setting
                if (syncable >= 0) ContentResolver.setIsSyncable(renamed, authority, syncable)
                ContentResolver.setSyncAutomatically(renamed, authority, automatic)
                for (sync: PeriodicSync in periodic) {
                    ContentResolver.addPeriodicSync(renamed, authority, sync.extras, sync.period)
                }
                if (automatic) ContentResolver.requestSync(renamed, authority, Bundle())
            }
        }, null)
    }

    /** Snapshot of a single account, or null if not found / no permission. */
    fun byIdentity(context: Context, identity: String): Snapshot? {
        return try {
            val am = AccountManager.get(context)
            val account = find(am, identity) ?: return null
            snapshotOf(am, account)
        } catch (_: SecurityException) {
            null
        }
    }

    /** First account whose stored server matches [server] exactly, or null. */
    fun byServer(context: Context, server: String): Snapshot? {
        return try {
            val am = AccountManager.get(context)
            for (a in am.getAccountsByType(TYPE)) {
                val s = am.getUserData(a, USER_DATA_SERVER) ?: continue
                if (s == server) return snapshotOf(am, a)
            }
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Every Mochi account on this device that we can read. */
    fun all(context: Context): List<Snapshot> {
        return try {
            val am = AccountManager.get(context)
            am.getAccountsByType(TYPE).mapNotNull { snapshotOf(am, it) }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /** Convenience: first available account (used by single-account adoption). */
    fun first(context: Context): Snapshot? = all(context).firstOrNull()

    /** Remove the identity's account. */
    fun remove(context: Context, identity: String) {
        try {
            val am = AccountManager.get(context)
            find(am, identity)?.let {
                am.removeAccountExplicitly(it)
            }
        } catch (_: SecurityException) {
        }
    }

    /**
     * Emits the account list on every AccountManager update. Does not emit on
     * collection, so a missing account at startup is not evidence of a logout -
     * the caller's own bootstrap owns the startup state.
     */
    fun accountsFlow(context: Context): Flow<List<Snapshot>> = callbackFlow {
        val am = AccountManager.get(context)
        val listener = OnAccountsUpdateListener { trySend(all(context)) }
        am.addOnAccountsUpdatedListener(listener, null, false)
        awaitClose { am.removeOnAccountsUpdatedListener(listener) }
    }

    private fun snapshotOf(am: AccountManager, account: Account): Snapshot? {
        val session = am.getPassword(account) ?: return null
        val server = am.getUserData(account, USER_DATA_SERVER) ?: return null
        val name = am.getUserData(account, USER_DATA_NAME).orEmpty()
        val fingerprint = am.getUserData(account, USER_DATA_FINGERPRINT)
        return Snapshot(identityOf(am, account), name, server, fingerprint, session)
    }
}
