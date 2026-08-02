// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * Run [action] against every one of [items], in order, and report whether all
 * of them succeeded. A failure is recorded and the run continues.
 *
 * Exists because `items.all { runCatching { ... }.isSuccess }` reads like this
 * and is not: [Iterable.all] short-circuits on the first false, so everything
 * after the first failure is silently never attempted. Applied to a batch
 * action that is the point — a screen reporting "some could not be accepted"
 * when most were never tried, and one permanently-failing element blocking the
 * whole batch on every retry, because each retry stops at the same element.
 *
 * Sequential rather than concurrent: these batches are short, and the server
 * calls behind them mutate a shared list.
 */
suspend fun <T> attemptAll(items: List<T>, action: suspend (T) -> Unit): Boolean {
    var succeeded = true
    for (item in items) {
        try {
            action(item)
        } catch (_: Exception) {
            succeeded = false
        }
    }
    return succeeded
}
