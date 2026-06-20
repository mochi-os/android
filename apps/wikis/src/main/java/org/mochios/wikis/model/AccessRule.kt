// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class AccessRule(
    val id: Int? = null,
    val subject: String = "",
    val operation: String = "",
    val grant: Int = 0,
    val name: String? = null,
    val isOwner: Boolean? = null,
)

data class AccessListResponse(
    val rules: List<AccessRule> = emptyList(),
)
