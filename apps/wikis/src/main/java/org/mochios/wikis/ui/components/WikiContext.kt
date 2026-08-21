// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import androidx.compose.runtime.compositionLocalOf
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions

/**
 * Per-wiki context provided on every entity-context screen; children read it
 * via `LocalWikiContext.current`.
 */
data class WikiContextValue(
    /** Entity id or fingerprint, whichever the route uses. */
    val wikiId: String,
    /** Loaded once at wiki entry via `/-/info` and cached in the host. */
    val info: WikiInfo,
    /** What the signed-in user is allowed to do in this wiki. */
    val permissions: WikiPermissions,
    /**
     * Origin of the server the session is bound to, without a trailing slash.
     */
    val serverUrl: String,
) {
    /**
     * Absolute URL prefix used for attachment downloads inside this wiki.
     * Always ends with `/-/` so callers can append `attachments/<id>` etc.
     */
    val baseURL: String get() = "$serverUrl/wikis/$wikiId/-/"

    /**
     * Resolve a markdown-relative attachment URL against [baseURL]:
     * `attachments/<id>`, `-/attachments/<id>` and
     * `/<entity>/-/attachments/<id>[/thumbnail]` are rewritten under this wiki;
     * absolute and external URLs pass through.
     */
    fun resolveAttachmentUrl(url: String): String {
        if (url.startsWith("attachments/")) {
            return "$baseURL$url"
        }
        if (url.startsWith("-/attachments/")) {
            return "$baseURL${url.substring(2)}"
        }
        val match = ATTACHMENT_RE.find(url)
        if (match != null) {
            val id = match.groupValues[1]
            val thumb = match.groupValues[2]
            return "${baseURL}attachments/$id$thumb"
        }
        return url
    }

    private companion object {
        /** Matches `/-/attachments/<id>` with optional `/thumbnail` suffix. */
        private val ATTACHMENT_RE = Regex("/-/attachments/([^/?#]+)(/thumbnail)?")
    }
}

/**
 * Current wiki context; null on class-level routes that have no wiki.
 */
val LocalWikiContext = compositionLocalOf<WikiContextValue?> { null }
