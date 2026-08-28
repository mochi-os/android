// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

import com.google.gson.annotations.SerializedName

data class DirectoryEntry(
    val id: String = "",
    val name: String = "",
    val fingerprint: String = "",
    @SerializedName("fingerprint_hyphens") val fingerprintHyphens: String = "",
    val location: String? = null,
    /**
     * Set only for an entry resolved from a mochi:// share link: the peer that
     * answered the probe. The join pins the sync to it.
     */
    val peer: String? = null,
)

data class DirectorySearchResponse(
    val results: List<DirectoryEntry> = emptyList(),
)
