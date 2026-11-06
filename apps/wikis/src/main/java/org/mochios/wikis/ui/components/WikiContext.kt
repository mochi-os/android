// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import androidx.compose.runtime.compositionLocalOf
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mochios.android.util.isServerOrigin
import org.mochios.android.util.segment
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
     * [baseURL] without the server: the base of the addresses the image
     * loader, the players and the file opener are given. Only a server-relative
     * address goes to the server with the session (see `owned`).
     */
    val path: String get() = "/wikis/$wikiId/-/"

    /**
     * [url] made server-relative when it is one of this wiki's own addresses
     * under [baseURL]; any other URL as given, which the loaders then ask
     * without the session.
     */
    fun local(url: String): String = if (url.startsWith(baseURL)) path + url.removePrefix(baseURL) else url

    /**
     * Resolve a markdown-relative attachment URL against [baseURL]:
     * `attachments/<id>`, `-/attachments/<id>` and
     * `/<entity>/-/attachments/<id>[/thumbnail]` are rewritten under this wiki;
     * absolute and external URLs pass through. Null for an attachment URL whose
     * id no segment can carry ([segment]), or that names more than an id and a
     * variant: a page is peer-authored, and an image request carries the
     * session and the token of whatever app its path names.
     */
    fun resolveAttachmentUrl(url: String): String? {
        val local = url.removePrefix("-/")
        if (local.startsWith("attachments/")) {
            val match = LOCAL_RE.matchEntire(local) ?: return null
            return attachment(match.groupValues[1], match.groupValues[2])
        }
        val match = ATTACHMENT_RE.find(url)
        if (match != null) {
            return attachment(match.groupValues[1], match.groupValues[2])
        }
        return url
    }

    /** This wiki's attachment [id], with [variant] after it, or null. */
    private fun attachment(id: String, variant: String): String? =
        segment(id)?.let { "${baseURL}attachments/$it$variant" }

    /**
     * Whether the lightbox may load [url], an address [resolveAttachmentUrl]
     * gave: one of this wiki's attachments, or an image on another host, which
     * the image loader asks without the session. Any other address on the
     * user's server names a route the page's author chose, and the loader
     * would ask it with the session and that app's token.
     */
    fun viewable(url: String): Boolean {
        if (url.startsWith(baseURL)) {
            // The URL resolves "." and "..", written plain or escaped, away.
            val id = OWN_RE.matchEntire(url.removePrefix(baseURL))?.groupValues?.get(1) ?: return false
            return id != "." && id != ".." && !id.contains("%2E", ignoreCase = true)
        }
        val address = url.toHttpUrlOrNull() ?: return false
        return !isServerOrigin(address, serverUrl)
    }

    private companion object {
        /** Matches `/-/attachments/<id>` with optional `/thumbnail` suffix. */
        private val ATTACHMENT_RE = Regex("/-/attachments/([^/?#]+)(/thumbnail)?")

        /** A relative `attachments/<id>`, with an optional variant, and nothing else. */
        private val LOCAL_RE = Regex("attachments/([^/?#]+)(/thumbnail|/preview)?")

        /** What [attachment] builds after [baseURL]: an escaped id and a variant. */
        private val OWN_RE = Regex("attachments/([A-Za-z0-9._~%-]+)(/thumbnail|/preview)?")
    }
}

/**
 * The address of attachment [id] under [baseURL], a wiki's (it ends in `-/`),
 * with [variant] after it, or null when no segment can carry the id
 * ([segment]).
 */
internal fun attachmentAt(baseURL: String, id: String, variant: String? = null): String? =
    segment(id)?.let { "${baseURL}attachments/$it" + if (variant == null) "" else "/$variant" }

/**
 * [attachmentAt] made server-relative, for the image loader, the players and
 * the file opener, which send only such an address with the session. Null when
 * [baseURL] will not parse or no segment can carry the id.
 */
internal fun attachmentPath(baseURL: String, id: String, variant: String? = null): String? {
    val path = baseURL.toHttpUrlOrNull()?.encodedPath ?: return null
    return attachmentAt(path, id, variant)
}

/**
 * Current wiki context; null on class-level routes that have no wiki.
 */
val LocalWikiContext = compositionLocalOf<WikiContextValue?> { null }
