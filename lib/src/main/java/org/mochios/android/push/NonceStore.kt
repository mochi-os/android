// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/**
 * Most nonces to keep outstanding; see [NonceStore].
 *
 * Well past the ~50 notifications Android will hold for one app, because
 * eviction here is a backstop rather than the mechanism: a nonce is retired
 * when its notification is tapped or dismissed, so the outstanding set tracks
 * the tray. At 64 it did not - dismissal retired nothing, so a busy feed
 * evicted nonces whose notifications were still on screen, and tapping one of
 * those did nothing at all.
 */
internal const val MAXIMUM_NONCES = 256

/**
 * Outstanding nonces after issuing [nonce]. Oldest first, so dropping from the
 * front past [maximum] retires the least recently posted notification.
 */
internal fun noncesAfterIssue(
    outstanding: List<String>,
    nonce: String,
    maximum: Int = MAXIMUM_NONCES,
): List<String> = (outstanding + nonce).takeLast(maximum)

/**
 * Outstanding nonces after spending [nonce], or null when it was not
 * outstanding - the signal to ignore the tap.
 */
internal fun noncesAfterConsume(outstanding: List<String>, nonce: String?): List<String>? {
    if (nonce.isNullOrEmpty() || nonce !in outstanding) return null
    return outstanding - nonce
}

/**
 * Single-use proofs that a `mochi:notification` tap came from a notification
 * this app posted: MainActivity is exported, so intent extras authenticate
 * nothing. Persistent, and bounded at [MAXIMUM_NONCES] - unspent nonces are
 * dropped oldest first.
 */
class NonceStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** Issue a nonce for a notification about to be posted. */
    fun issue(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        // URL-safe base64 is [A-Za-z0-9-_], so a value can never contain the
        // separator the outstanding list is stored with.
        val nonce = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        synchronized(lock) {
            write(noncesAfterIssue(read(), nonce))
        }
        return nonce
    }

    /** True if [nonce] was outstanding; it is spent either way. */
    fun consume(nonce: String?): Boolean {
        synchronized(lock) {
            val remaining = noncesAfterConsume(read(), nonce) ?: return false
            write(remaining)
            return true
        }
    }

    private fun read(): List<String> =
        preferences.getString(KEY, "").orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotEmpty() }

    private fun write(nonces: List<String>) {
        preferences.edit().putString(KEY, nonces.joinToString(SEPARATOR)).apply()
    }

    private companion object {
        const val PREFERENCES = "mochi_notification_taps"
        const val KEY = "nonces"
        const val SEPARATOR = ","

        // Both push transports post from their own threads.
        val lock = Any()
    }
}
