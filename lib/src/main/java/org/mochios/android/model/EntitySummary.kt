// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

data class EntitySummary(
    val id: String,
    val name: String = "",
    val description: String = "",
    val server: String? = null,
    val subscribers: Int = 0,
    val isSubscribed: Boolean = false
)
