// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mochios.android.R as MochiR
import org.mochios.android.api.OwnedAddress
import org.mochios.android.model.Attachment as MochiAttachment
import org.mochios.android.util.AttachmentOpener
import org.mochios.android.util.isServerOrigin
import org.mochios.android.util.segment
import org.mochios.wikis.model.Attachment
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
     * loader, the players and the file opener are given. Only an
     * [OwnedAddress] goes to the server with a credential.
     */
    val path: String get() = "/wikis/$wikiId/-/"

    /**
     * [url] as a Coil model: one of this wiki's own addresses under [baseURL]
     * made an [OwnedAddress], fetched with a credential; any other URL as
     * given, which the loaders then ask without one.
     */
    fun local(url: String): Any =
        if (url.startsWith(baseURL)) OwnedAddress.of(path + url.removePrefix(baseURL)) ?: url else url

    /**
     * The image model for a page body's image [destination]: one of this
     * wiki's attachments, however the page wrote it, as an [OwnedAddress],
     * loaded with a credential as the wiki's attachment routes require; null
     * for any other address, which the body's loader then asks without one.
     */
    fun image(destination: String): OwnedAddress? {
        val resolved = resolveAttachmentUrl(destination) ?: return null
        if (identify(resolved) == null) return null
        return local(resolved) as? OwnedAddress
    }

    /**
     * The escaped id of the attachment [url] names when it is one of this
     * wiki's own under [baseURL], an address [resolveAttachmentUrl] gave, with
     * or without a variant; null for any other address.
     */
    fun identify(url: String): String? {
        if (!url.startsWith(baseURL)) return null
        // The URL resolves "." and "..", written plain or escaped, away.
        val id = OWN_RE.matchEntire(url.removePrefix(baseURL))?.groupValues?.get(1) ?: return null
        if (id == "." || id == ".." || id.contains("%2E", ignoreCase = true)) return null
        return id
    }

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
        if (url.startsWith(baseURL)) return identify(url) != null
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
 * [attachmentAt] made server-relative, one of the app's own addresses, for the
 * image loader, the players and the file opener, which send only such an
 * address with a credential. Null when [baseURL] will not parse or no segment
 * can carry the id.
 */
internal fun attachmentPath(baseURL: String, id: String, variant: String? = null): OwnedAddress? {
    val path = baseURL.toHttpUrlOrNull()?.encodedPath ?: return null
    return OwnedAddress.of(attachmentAt(path, id, variant))
}

/**
 * The markdown that embeds [attachment] in a page, as the web writes it: an
 * image's thumbnail as an image, anything else as a link, the name escaped
 * ([escapeLabel]) so a bracket in it cannot end the link text early.
 */
internal fun attachmentMarkdown(attachment: Attachment): String {
    val url = "attachments/" + segment(attachment.id).orEmpty()
    val label = escapeLabel(attachment.name)
    return if (attachment.type.startsWith("image/")) "![$label]($url/thumbnail)" else "[$label]($url)"
}

/** [text] with `\`, `[` and `]` escaped, to stand as markdown link text. */
internal fun escapeLabel(text: String): String =
    text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")

/**
 * [this] as the shared library's attachment, which its gallery and file
 * opener take.
 */
internal fun Attachment.library(): MochiAttachment = MochiAttachment(
    id = id,
    name = name,
    size = size,
    type = type,
    created = created,
    caption = caption,
)

/**
 * Show a file the app has no viewer of its own for. The bytes at [address],
 * one of the wiki's own, are fetched through the authenticated asset client,
 * cached, and handed to whatever app on the device can display them - the same
 * route a file in a chat message takes. Downloading is the attachments menu's
 * own entry; this only borrows a viewer.
 *
 * @param scope Scope the fetch runs in, cancelled with the screen.
 * @param context Context the viewer is launched from.
 * @param address The attachment's address on the user's server.
 * @param attachment The file to show: its name and type choose the viewer.
 * @param onNoApp Called with the cached copy when no app on the device can open it.
 */
internal fun openAttachment(
    scope: CoroutineScope,
    context: Context,
    address: OwnedAddress,
    attachment: MochiAttachment,
    onNoApp: (AttachmentOpener.UnopenedFile) -> Unit,
) {
    Toast.makeText(
        context,
        context.getString(MochiR.string.common_opening_file),
        Toast.LENGTH_SHORT,
    ).show()
    scope.launch {
        val result = AttachmentOpener.open(context, address, attachment)
        if (result == AttachmentOpener.OpenResult.NO_APP) {
            onNoApp(AttachmentOpener.unopened(context, attachment))
        }
        val message = when (result) {
            AttachmentOpener.OpenResult.NO_APP -> null
            AttachmentOpener.OpenResult.FAILED -> MochiR.string.common_file_open_failed
            AttachmentOpener.OpenResult.UNAVAILABLE -> MochiR.string.common_attachment_unavailable
            AttachmentOpener.OpenResult.OPENED -> null
        }
        if (message != null) {
            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Current wiki context; null on class-level routes that have no wiki.
 */
val LocalWikiContext = compositionLocalOf<WikiContextValue?> { null }
