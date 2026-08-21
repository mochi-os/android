// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * Run [action] against every one of [items] in order and report whether all
 * succeeded; a failure is recorded and the run continues. `items.all {
 * runCatching { ... }.isSuccess }` looks equivalent but short-circuits on the
 * first failure.
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
