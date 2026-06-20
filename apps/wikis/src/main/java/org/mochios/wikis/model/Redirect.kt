// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class Redirect(
    val source: String = "",
    val target: String = "",
    val created: Long = 0,
)

data class RedirectsResponse(
    val redirects: List<Redirect> = emptyList(),
)

data class RedirectSetResponse(
    val ok: Boolean = false,
)

data class RedirectDeleteResponse(
    val ok: Boolean = false,
)
