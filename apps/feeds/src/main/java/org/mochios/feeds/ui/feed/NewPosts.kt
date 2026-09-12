// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * The count behind the "new posts" pill. Websocket frames arrive on the
 * socket's own thread, so both the seen set and the counter have to be safe
 * to touch from there.
 */
internal class NewPosts {
    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    /**
     * Count a `post/create` frame for [post]. An absent id is the batch form
     * (sent when AI tagging is off) and has nothing to reconcile against, so
     * it always counts. Otherwise count the post once, and only when it is not
     * already on screen — RSS ingestion inserts the row immediately but defers
     * the frame until tagging finishes, so a load in between sees the post
     * before its frame and counting it would raise a phantom pill.
     */
    fun record(post: String?, loaded: (String) -> Boolean) {
        if (post.isNullOrEmpty()) {
            _count.update { it + 1 }
            return
        }
        if (seen.add(post) && !loaded(post)) _count.update { it + 1 }
    }

    /** Everything queued is now on screen. */
    fun clear() {
        seen.clear()
        _count.value = 0
    }
}
