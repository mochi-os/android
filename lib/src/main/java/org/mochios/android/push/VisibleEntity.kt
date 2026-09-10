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

    /**
     * How a resumed screen covers a push about the entity it shows.
     */
    enum class Cover {

        /** Nothing is showing it; post the notification. */
        NONE,

        /** Showing it; drop the tray row, but leave the row unread. */
        SUPPRESS,

        /** Showing exactly it; drop the row and mark it read as well. */
        READ,
    }

    /**
     * An app slug and the path a resumed screen is showing, in segments. A
     * screen names as much of the path as identifies what it shows: a feed is
     * one segment, a market message thread is three, because market puts a
     * category where other apps put the entity.
     */
    data class Showing(
        val app: String,
        val path: List<String>,
        val marksRead: Boolean,
        val socketKey: String,
    )

    private val showing = ConcurrentHashMap<Any, Showing>()

    /**
     * Record that the screen identified by [token] is resumed on [path].
     *
     * @param token identifies the screen, so two screens on one entity keep
     *   separate registrations; pass the same value to [hide].
     * @param path what the screen shows, under the app: an entity id, or a
     *   longer path where that is what identifies it. A blank path registers
     *   nothing.
     * @param socketKey the key this screen's websocket subscribes with, which
     *   is what makes suppressing honest - see [coverFor]. Not always the
     *   entity in the link: chat and the games subscribe with a record key,
     *   market with `market-thread-<id>`.
     */
    fun show(
        token: Any,
        app: String,
        path: String,
        marksRead: Boolean = false,
        socketKey: String,
    ) {
        if (app.isEmpty() || socketKey.isEmpty()) return
        val segments = path.split('/').filter { segment -> segment.isNotEmpty() }
        if (segments.isEmpty()) return
        showing[token] = Showing(app, segments, marksRead, socketKey)
    }

    /** Drop [token]'s registration; its screen has paused or left. */
    fun hide(token: Any) {
        showing.remove(token)
    }

    /**
     * How a push about [link] is covered by what is on screen.
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
     * A screen only covers a push while its socket is up. Dropping a tray row
     * is a promise that the content is arriving another way; with the socket
     * down nothing arrives, and the user is told nothing at all. So a
     * registration whose [Showing.socketKey] is not live is ignored here
     * rather than trusted.
     *
     * @param link the payload's link, `/<app>/<entity>[/<sub>...]`.
     * @param isLive whether a key's socket is connected and carrying events;
     *   the receivers pass `MochiWebSocket::isLive`.
     * @return [Cover.READ] when a screen showing exactly what the link names
     *   is up, [Cover.SUPPRESS] when the screen shows something containing it,
     *   and [Cover.NONE] when nothing does.
     */
    fun coverFor(link: String, isLive: (String) -> Boolean): Cover {
        val segments = link.split('/').filter { segment -> segment.isNotEmpty() }
        if (segments.size < 2) return Cover.NONE
        val app = segments[0]
        val path = segments.drop(1)
        var cover = Cover.NONE
        for (shown in showing.values) {
            if (shown.app != app) continue
            if (!isLive(shown.socketKey)) continue
            // A screen covers everything under what it shows, so a wiki covers
            // its pages; naming more segments narrows it to one of them.
            if (path.size < shown.path.size) continue
            if (path.subList(0, shown.path.size) != shown.path) continue
            if (shown.marksRead) return Cover.READ
            cover = Cover.SUPPRESS
        }
        return cover
    }
}
