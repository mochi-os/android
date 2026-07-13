// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import coil3.map.Mapper
import coil3.request.Options
import org.mochios.android.auth.SessionManager

/**
 * Coil [Mapper] that expands a server-relative asset path
 * ("/people/<id>/-/avatar", "/market/-/photo/<id>/thumbnail", …) into an
 * absolute URL against the session server. Registering it on the singleton
 * `ImageLoader` lets any `AsyncImage(model = "/...")` or
 * `EntityAvatar(src = "/...")` resolve without each call site prefixing the host.
 *
 * Only single-leading-slash strings are rewritten. Absolute URLs and every other
 * string form (`https://`, `content://`, `file://`, `data:`, protocol-relative
 * `//host`) return `null`, meaning "not mine — pass through unchanged". Non-string
 * models (`File`, `Uri`, resource ids) never reach this mapper, since Coil
 * dispatches mappers by type.
 */
class RelativeAssetUrlMapper(
    private val sessionManager: SessionManager,
) : Mapper<String, String> {

    override fun map(data: String, options: Options): String? {
        if (!data.startsWith("/") || data.startsWith("//")) return null
        val base = sessionManager.getServerUrlBlocking().trimEnd('/')
        return if (base.isBlank()) null else "$base$data"
    }
}
