// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * One value read from the server, shared by the screens that ask for it. Callers
 * asking while a read is under way wait on it, and its answer is kept for
 * [lifetime] milliseconds, so screens opening together make one request.
 *
 * @param lifetime how long an answer is handed out before it is read again.
 * @param scope where reads run, so a caller that leaves does not cancel the
 *   read others wait on.
 * @param read the request itself.
 */
internal class SharedRead<T : Any>(
    private val lifetime: Long,
    private val scope: CoroutineScope,
    private val read: suspend () -> T,
) {

    private var pending: Deferred<T>? = null
    private var answer: T? = null
    private var answered = 0L

    /**
     * The value, from the held answer while it is young, else from the read
     * under way or a new one. A [fresh] call always reads, and its answer
     * replaces the held one.
     */
    suspend fun get(fresh: Boolean = false): T {
        val deferred = synchronized(this) {
            val held = answer
            if (!fresh && held != null && SystemClock.elapsedRealtime() - answered < lifetime) {
                return held
            }
            pending?.takeIf { running -> !fresh && running.isActive }
                ?: scope.async { read() }.also { started -> pending = started }
        }
        val value = deferred.await()
        synchronized(this) {
            if (pending === deferred) {
                answer = value
                answered = SystemClock.elapsedRealtime()
                pending = null
            }
        }
        return value
    }

    /** Holds [value], the server's answer to a change, as the latest read. */
    fun put(value: T) {
        synchronized(this) {
            answer = value
            answered = SystemClock.elapsedRealtime()
            pending = null
        }
    }

    /** Drops the held answer and any read begun before a change. */
    fun forget() {
        synchronized(this) {
            answer = null
            pending = null
        }
    }
}
