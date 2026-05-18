package org.mochios.wikis.ui.components

import androidx.compose.runtime.compositionLocalOf
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions

/**
 * Per-wiki context passed down the composition tree. Mirrors web's
 * `WikiBaseURLContext` + `WikiContext`
 * (`apps/wikis/web/src/context/wiki-base-url-context.tsx`,
 * `apps/wikis/web/src/context/wiki-context.tsx`).
 *
 * Web splits this in two because the markdown renderer needs a stable
 * `baseURL` whether or not the React-Query info hook has finished — on
 * Android the wikis nav graph holds onto the loaded [WikiInfo] and the
 * cached server URL, so a single immutable value is enough.
 *
 * The wikis nav graph provides this on every entity-context screen. Wave 2
 * screens (MarkdownContent, AttachmentsScreen, comment threads,
 * WikiSettings, ...) read it with
 * `val wiki = LocalWikiContext.current ?: error("no wiki context")`.
 */
data class WikiContextValue(
    /** Entity id or fingerprint, whichever the route uses. */
    val wikiId: String,
    /** Loaded once at wiki entry via `/-/info` and cached in the host. */
    val info: WikiInfo,
    /** What the signed-in user is allowed to do in this wiki. */
    val permissions: WikiPermissions,
    /**
     * Origin of the Mochi server the session is bound to.
     * Sourced from `SessionManager.getServerUrlBlocking().trimEnd('/')`.
     */
    val serverUrl: String,
) {
    /**
     * Absolute URL prefix used for attachment downloads inside this wiki.
     * Always ends with `/-/` so callers can append `attachments/<id>` etc.
     */
    val baseURL: String get() = "$serverUrl/wikis/$wikiId/-/"

    /**
     * Resolve a markdown-relative attachment URL to an absolute URL.
     *
     * Mirrors the web `resolveAttachmentUrl` in
     * `apps/wikis/web/src/features/wiki/markdown-content.tsx` lines 28-40:
     *  - `attachments/<id>` -> `<baseURL>attachments/<id>`
     *  - `-/attachments/<id>` -> `<baseURL>attachments/<id>` (strip "-/")
     *  - Full `/<entity>/-/attachments/<id>[/thumbnail]` -> rewrite under
     *    this wiki's baseURL (regex extract id + optional `/thumbnail`)
     *  - Anything else (absolute http/https or external) -> pass-through.
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
 * Current wiki context, or `null` outside an entity-context wikis screen
 * (class-level routes like `WikisFeature.HOME` / `FIND` / `JOIN`). Consumers
 * inside entity-context screens may safely error if it's missing.
 */
val LocalWikiContext = compositionLocalOf<WikiContextValue?> { null }
