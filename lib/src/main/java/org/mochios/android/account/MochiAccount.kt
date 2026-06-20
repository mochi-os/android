// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Cross-app session sharing for Mochi apps via Android's AccountManager.
 *
 * All Mochi apps register the same authenticator service with account type
 * [TYPE]. One device-elected app hosts the authenticator process; the others
 * read sessions via signature-protected IPC. Same signing key (debug or
 * release) is the only access guard.
 *
 * Account naming: [Account.name] is the user's identity entity ID — globally
 * unique across the Mochi network. Display name and server URL live in
 * [USER_DATA_NAME] / [USER_DATA_SERVER]. Multiple accounts (different
 * identities, different servers) coexist; helpers below expose the right
 * lookup primitives without collapsing the set.
 */
object MochiAccount {

    /** Single account type for all Mochi accounts. */
    const val TYPE = "org.mochios.account"

    /** Auth-token type for the raw session cookie. */
    const val TOKEN_SESSION = "session"

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

    /**
     * Insert or update the account keyed by [identity]. Any existing account
     * with the same identity is updated in place; other accounts are left
     * untouched (multi-account support).
     */
    fun upsert(
        context: Context,
        identity: String,
        name: String,
        server: String,
        fingerprint: String?,
        session: String
    ) {
        if (identity.isBlank()) return
        val am = AccountManager.get(context)
        val account = Account(identity, TYPE)
        val existing = am.getAccountsByType(TYPE).firstOrNull { it.name == identity }
        if (existing == null) {
            val data = Bundle().apply {
                putString(USER_DATA_NAME, name)
                putString(USER_DATA_SERVER, server)
                if (fingerprint != null) putString(USER_DATA_FINGERPRINT, fingerprint)
            }
            am.addAccountExplicitly(account, session, data)
        } else {
            am.setPassword(account, session)
            am.setUserData(account, USER_DATA_NAME, name)
            am.setUserData(account, USER_DATA_SERVER, server)
            if (fingerprint != null) am.setUserData(account, USER_DATA_FINGERPRINT, fingerprint)
        }
    }

    /** Snapshot of a single account, or null if not found / no permission. */
    fun byIdentity(context: Context, identity: String): Snapshot? {
        return try {
            val am = AccountManager.get(context)
            val account = am.getAccountsByType(TYPE).firstOrNull { it.name == identity }
                ?: return null
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

    /** Remove the account whose name == identity. */
    fun remove(context: Context, identity: String) {
        try {
            val am = AccountManager.get(context)
            am.getAccountsByType(TYPE).firstOrNull { it.name == identity }?.let {
                am.removeAccountExplicitly(it)
            }
        } catch (_: SecurityException) {
        }
    }

    /** Wipe every Mochi account on the device. */
    fun removeAll(context: Context) {
        try {
            val am = AccountManager.get(context)
            for (a in am.getAccountsByType(TYPE)) {
                am.removeAccountExplicitly(a)
            }
        } catch (_: SecurityException) {
        }
    }

    /**
     * A flow of Mochi account snapshots that emits whenever AccountManager
     * fires an updated event. Useful for ViewModels that want to react to
     * cross-app logins (an account appears) and logouts (an account vanishes)
     * without juggling listeners + lifecycle by hand.
     *
     * The flow does NOT emit synchronously on collection — only on actual
     * changes — so an app that boots into a stale "bound identity but no
     * account" state isn't immediately taken as evidence of a cross-app
     * logout (a missing record can mean "never written yet" too). The
     * caller's own bootstrap is responsible for the startup state.
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
        return Snapshot(account.name, name, server, fingerprint, session)
    }
}
