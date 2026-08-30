// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

/**
 * Response of `{wiki}/-/share`: the shareable [link] the server assembles,
 * plus the [peer] and [wiki] it is built from.
 */
data class ShareResponse(
    val link: String = "",
    val peer: String = "",
    val wiki: String = "",
)
