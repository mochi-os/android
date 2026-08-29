// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

/**
 * JSON body of `POST -/subscribe`. A null [server] is omitted from the payload
 * - the retry after a 502 resends without the hint.
 */
data class SubscribeRequest(
    val target: String,
    val server: String? = null,
    /**
     * The peer a mochi:// share link named. action_join pins the sync to it,
     * which is the only way to reach a wiki on a server this instance has not
     * already learned about.
     */
    val peer: String? = null,
)
