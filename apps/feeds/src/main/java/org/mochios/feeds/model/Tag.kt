// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.model

data class Tag(
    val id: String = "",
    val label: String = "",
    val qid: String? = null,
    val source: String? = null,
    val relevance: Double? = null,
    val interest: Double? = null,
    val count: Int = 0
)
