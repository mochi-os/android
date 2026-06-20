// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * Optional per-person presentation tweaks. Only the accent colour is
 * currently used; the type is open-ended so additional style hints can
 * be added without breaking existing clients.
 */
data class PersonStyle(
    val accent: String? = null
)
