// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class WikiSettings(
    val home: String = "",
    val source: String? = null,
    val extra: Map<String, String> = emptyMap(),
)

data class SettingsResponse(
    val settings: WikiSettings = WikiSettings(),
)

data class SettingsSetResponse(
    val ok: Boolean = false,
)

data class SyncResponse(
    val ok: Boolean = false,
    val message: String = "",
)

data class SubscribeResponse(
    val ok: Boolean = false,
    val message: String = "",
)
