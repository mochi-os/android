// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

/**
 * What `-/probe` answers for a pasted `mochi://<peer>/<wiki>` link: the remote
 * wiki as the directory would have described it, plus the peer that answered.
 * The join has to pin that same peer.
 */
data class ProbeResponse(
    val id: String = "",
    val name: String = "",
    val fingerprint: String = "",
    val peer: String = "",
    val remote: Boolean = false,
)
