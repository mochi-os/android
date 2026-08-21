// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-slot buffer for a notification-tap deep link, a path like
 * `/feeds/<id>/posts/<post>`. MainActivity writes it in `onCreate` before
 * bootstrap has state, so navigation must only consume it after the Ready
 * transition.
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
