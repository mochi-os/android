// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/** Most nonces to keep outstanding; see [NonceStore]. */
internal const val MAXIMUM_NONCES = 64

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
 * outstanding — which is the signal to ignore the tap. Returning a new list
 * rather than mutating is what makes single use testable: consuming the same
 * nonce against the result fails.
 */
internal fun noncesAfterConsume(outstanding: List<String>, nonce: String?): List<String>? {
    if (nonce.isNullOrEmpty() || nonce !in outstanding) return null
    return outstanding - nonce
}

/**
 * Single-use proofs that a `mochi:notification` tap came from a notification
 * this app posted.
 *
 * MainActivity is exported — it has to be, to receive the `mochi:` scheme at
 * all — so any app or web page can send it an intent, and extras on that intent
 * authenticate nothing. Every notification therefore carries an unguessable
 * nonce issued here, and the tap handler acts only if it can consume one.
 *
 * Persistent because notifications are posted while the app is dead, and
 * bounded because nonces for notifications the user never taps are never
 * consumed: past [MAXIMUM_NONCES] the oldest are dropped, which costs at worst
 * the tap action on a very stale tray entry.
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
