// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

/**
 * Request body for `-/create`. [opponents] is a comma-joined list of entity
 * ids; [language] is "en_US" or "en_UK".
 */
data class CreateGameRequest(
    val opponents: String = "",
    val language: String = "en_US",
)
