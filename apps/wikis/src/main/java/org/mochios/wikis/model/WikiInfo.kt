// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class WikiInfo(
    val id: String = "",
    val name: String = "",
    val home: String = "",
    val fingerprint: String? = null,
    val source: String? = null,
)

data class WikiInfoResponse(
    val entity: Boolean = false,
    val wiki: WikiInfo? = null,
    val wikis: List<WikiInfo>? = null,
    val permissions: WikiPermissions? = null,
    val fingerprint: String? = null,
)
