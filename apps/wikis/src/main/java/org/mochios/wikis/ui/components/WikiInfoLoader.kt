// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import org.mochios.wikis.model.WikiInfoResponse

/**
 * The wiki's information every wiki screen needs beside its own content: the
 * wiki and what the reader may do in it. A screen's load calls [ensure] first
 * and fetches its content after, in the one function its Retry calls, so an
 * information request that failed is asked again with the content instead of
 * leaving the screen waiting for an answer that will never come.
 *
 * [loaded] runs once, when the information first arrives - to store it in the
 * screen's state and start following the wiki's live updates.
 */
class WikiInfoLoader(
    private val fetch: suspend () -> WikiInfoResponse,
    private val loaded: (WikiInfoResponse) -> Unit,
) {
    /** Whether the information has arrived. */
    var held = false
        private set

    /** Fetches the information unless it is already held; throws what the request threw. */
    suspend fun ensure() {
        if (held) return
        val response = fetch()
        held = true
        loaded(response)
    }
}
