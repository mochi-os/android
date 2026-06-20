// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.model

data class Source(
    val id: String = "",
    val feed: String = "",
    val type: String = "",
    val url: String = "",
    val name: String = "",
    val credibility: Double = 0.0,
    val interval: Int = 0,
    val fetched: Long = 0,
    val transform: String = ""
)
