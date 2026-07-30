// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

import com.google.gson.annotations.SerializedName

data class Recommendation(
    val id: String = "",
    val name: String = "",
    val blurb: String = "",
    val fingerprint: String = "",
    @SerializedName("fingerprint_hyphens") val fingerprintHyphens: String = "",
    val server: String = "",
)

data class RecommendationsResponse(
    val wikis: List<Recommendation> = emptyList(),
)
