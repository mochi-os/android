// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

/**
 * Mirrors `ConfigEntry` in `apps/staff/web/src/types/config.ts`. Values are
 * opaque strings; secrets are returned as "" and the UI shows only whether one
 * is set.
 */
data class ConfigEntry(
    val key: String = "",
    val value: String = "",
)
