// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Most nonces to keep outstanding; see [NonceStore].
 *
 * A backstop, not the mechanism: each tray tag holds one nonce, replaced when
 * the tag is posted again and retired when its notification is tapped,
 * swiped away or cancelled by the app, so the outstanding set tracks the tray.
 */
internal const val MAXIMUM_NONCES = 256

/**
 * One outstanding nonce and the tray tag of the notification carrying it.
 * [tag] is blank for a nonce no notification carries - [org.mochios.android.launcher.openInApp]
 * issues one for a link tapped in the app - which nothing replaces.
 */
internal data class Outstanding(val tag: String, val nonce: String)

/**
 * Outstanding nonces after issuing [nonce] for [tag]. A notification posted
 * again under its tag replaces the one before, which fires no delete intent,
 * so its nonce goes with it. Oldest first, so dropping from the front past
 * [maximum] retires the least recently posted.
 */
internal fun noncesAfterIssue(
    outstanding: List<Outstanding>,
    tag: String,
    nonce: String,
    maximum: Int = MAXIMUM_NONCES,
): List<Outstanding> = (noncesAfterRetire(outstanding, tag) + Outstanding(tag, nonce)).takeLast(maximum)

/** Outstanding nonces after [tag]'s notification is cancelled by the app. */
internal fun noncesAfterRetire(outstanding: List<Outstanding>, tag: String): List<Outstanding> =
    if (tag.isBlank()) outstanding else outstanding.filter { entry -> entry.tag != tag }

/**
 * Outstanding nonces after spending [nonce], or null when it was not
 * outstanding - the signal to ignore the tap.
 */
internal fun noncesAfterConsume(outstanding: List<Outstanding>, nonce: String?): List<Outstanding>? {
    if (nonce.isNullOrEmpty() || outstanding.none { entry -> entry.nonce == nonce }) return null
    return outstanding.filter { entry -> entry.nonce != nonce }
}

/**
 * The outstanding list as stored. A value written before nonces were keyed by
 * tag is a comma-separated list of nonces, read as tagless so the taps of
 * notifications already in the tray still work.
 */
internal fun noncesFromStored(stored: String): List<Outstanding> {
    if (!stored.startsWith("[")) {
        return stored.split(',').filter { it.isNotEmpty() }.map { nonce -> Outstanding("", nonce) }
    }
    return runCatching {
        val array = JSONArray(stored)
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val nonce = entry.optString("nonce").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Outstanding(entry.optString("tag"), nonce)
        }
    }.getOrDefault(emptyList())
}

internal fun noncesToStored(outstanding: List<Outstanding>): String =
    JSONArray(outstanding.map { entry -> JSONObject().put("tag", entry.tag).put("nonce", entry.nonce) }).toString()

/**
 * Single-use proofs that a `mochi:notification` tap came from a notification
 * this app posted: MainActivity is exported, so intent extras authenticate
 * nothing. Persistent, one per tray tag, and bounded at [MAXIMUM_NONCES].
 */
class NonceStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Issue a nonce for a notification about to be posted under [tag], blank
     * for one no notification carries.
     */
    fun issue(tag: String = ""): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val nonce = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        synchronized(lock) {
            write(noncesAfterIssue(read(), tag, nonce))
        }
        return nonce
    }

    /** Retire [tag]'s nonce: the app has cancelled its notification. */
    fun retire(tag: String) {
        synchronized(lock) {
            val outstanding = read()
            val remaining = noncesAfterRetire(outstanding, tag)
            if (remaining.size != outstanding.size) write(remaining)
        }
    }

    /** True if [nonce] was outstanding; it is spent either way. */
    fun consume(nonce: String?): Boolean {
        synchronized(lock) {
            val remaining = noncesAfterConsume(read(), nonce) ?: return false
            write(remaining)
            return true
        }
    }

    private fun read(): List<Outstanding> = noncesFromStored(preferences.getString(KEY, "").orEmpty())

    private fun write(outstanding: List<Outstanding>) {
        preferences.edit().putString(KEY, noncesToStored(outstanding)).apply()
    }

    private companion object {
        const val PREFERENCES = "mochi_notification_taps"
        const val KEY = "nonces"

        // Both push transports post from their own threads.
        val lock = Any()
    }
}
