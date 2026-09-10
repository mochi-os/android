// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import java.util.concurrent.ConcurrentHashMap

/**
 * The entities whose screens are in front of the user right now, so a push
 * about one of them is not posted to the tray - the socket has already
 * delivered it to the screen they are reading.
 *
 * Registrations are keyed by an opaque token, one per screen, rather than by
 * the entity: a feed and a post of that feed name the same entity, and on the
 * way into the post the arriving screen registers before the outgoing one
 * pauses. Keyed by entity, that pause would cancel the cover the post had just
 * taken out.
 *
 * Both receivers run in the app's own process, so this map is all the sharing
 * they need. When the process is dead nothing is on screen and every push
 * posts, which is the right answer.
 */
object VisibleEntity {

    /** An app slug and the entity a resumed screen is showing. */
    data class Showing(val app: String, val objectId: String)

    private val showing = ConcurrentHashMap<Any, Showing>()

    /**
     * Record that the screen identified by [token] is resumed on [objectId].
     *
     * @param token identifies the screen, so two screens on one entity keep
     *   separate registrations; pass the same value to [hide].
     */
    fun show(token: Any, app: String, objectId: String) {
        if (app.isEmpty() || objectId.isEmpty()) return
        showing[token] = Showing(app, objectId)
    }

    /** Drop [token]'s registration; its screen has paused or left. */
    fun hide(token: Any) {
        showing.remove(token)
    }

    /**
     * Whether a push is about an entity on screen.
     *
     * Matched on [link] alone. The payload's other identifiers cannot do this
     * job: `app` is the sending app's fingerprint rather than its slug, and
     * the tray tag is `<app fingerprint>-<topic>-<object fingerprint>`, whose
     * object is the post or page rather than the feed or wiki holding it. A
     * real pair from this server:
     *
     *     tag  = 12254aHfG39Lqriz...-post-1mLb3RWUGooffbyB...
     *     link = /feeds/qM8KdfhEt
     *
     * Only the link names the app and the entity a screen can be open on.
     *
     * @param link the payload's link, `/<app>/<entity>[/<sub>...]`.
     * @return true when the tray row would only repeat what is already open.
     */
    fun covers(link: String): Boolean {
        val segments = link.trim('/').split('/')
        if (segments.size < 2) return false
        val app = segments[0]
        val objectId = segments[1]
        return showing.values.any { shown -> shown.app == app && shown.objectId == objectId }
    }
}
