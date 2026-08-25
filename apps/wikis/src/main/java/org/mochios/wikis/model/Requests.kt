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
)

/**
 * JSON body of `POST -/rss/token/revoke`. [entity] scopes the revoke the way
 * it scopes minting - `*` for the all-wikis feed, else a wiki id or
 * fingerprint.
 */
data class RssRevokeRequest(
    val entity: String,
)
