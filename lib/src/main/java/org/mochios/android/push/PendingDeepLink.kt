// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-slot buffer for a notification-tap deep-link target. Written
 * by MainActivity when an `Intent` with the notification scheme arrives
 * (cold-start or warm), consumed by the per-app navigation once the
 * AppBootstrapHost has resolved.
 *
 * The buffer holds a *path* like `/feeds/<id>/posts/<post>` — the same
 * shape the server passes as the `url` argument to
 * `mochi.service.call("notifications", "send", ...)`. Each app's
 * navigation decides how to interpret it.
 *
 * Cold-start invariant: the intent is delivered to `onCreate` before
 * AppBootstrapHost has any state, so we must NOT consume it during
 * bootstrap. Navigation should take it after the Ready transition.
 */
object PendingDeepLink {

    private val _link = MutableStateFlow<String?>(null)
    val link = _link.asStateFlow()

    fun set(path: String) {
        _link.value = path
    }

    /** Read once and clear. Returns null if no pending link. */
    fun consume(): String? {
        val current = _link.value
        if (current != null) _link.value = null
        return current
    }
}
