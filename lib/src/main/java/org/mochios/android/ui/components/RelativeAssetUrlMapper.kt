// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import coil3.map.Mapper
import coil3.request.Options
import org.mochios.android.auth.SessionManager

/**
 * Coil [Mapper] expanding a server-relative asset path
 * ("/people/<id>/-/avatar") into an absolute URL against the session server, so
 * call sites need no host. Anything else returns null, meaning "not mine - pass
 * through unchanged".
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
