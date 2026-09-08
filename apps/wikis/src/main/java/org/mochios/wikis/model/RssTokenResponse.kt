// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

/**
 * Answer of `-/rss/token`. The server keeps only the token's hash, so a feed
 * URL already handed out cannot be shown again: it answers [exists] instead,
 * with [token] empty, and leaves it to the caller to decide whether to replace
 * it. Re-issuing silently would break whatever reader is polling the old URL.
 */
data class RssTokenResponse(
    val token: String = "",
    val exists: Boolean = false,
)
