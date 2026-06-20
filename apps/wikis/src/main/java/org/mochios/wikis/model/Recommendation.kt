// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class Recommendation(
    val id: String = "",
    val name: String = "",
    val blurb: String = "",
    val fingerprint: String = "",
    val server: String = "",
)

data class RecommendationsResponse(
    val wikis: List<Recommendation> = emptyList(),
)
