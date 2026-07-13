// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class Replica(
    val id: String = "",
    val name: String? = null,
    val subscribed: Long = 0,
    val synced: Long = 0,
)
