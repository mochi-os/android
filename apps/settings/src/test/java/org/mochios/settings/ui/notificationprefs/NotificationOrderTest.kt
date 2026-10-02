// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.settings.api.NotifCategory
import org.mochios.settings.api.NotifTopic
import org.mochios.settings.api.NotifTopicApp

/** The order categories and topics are listed in, matching the web's. */
class NotificationOrderTest {

    private fun category(id: String, label: String, display: String = "", default: Int = 0) =
        NotifCategory(id = id, label = label, display = display, default = default)

    /** The categories used to read in the order the server happened to return them. */
    @Test
    fun `categories read by the name shown, numbers in numeric order, with No notifications last`() {
        val categories = listOf(
            category("0", "No notifications"),
            category("a", "Zebra", default = 1),
            category("b", "Group 10"),
            category("c", "group 2"),
            category("d", "Normal", display = "Ärger"),
            category("e", "Bulk"),
        )
        assertEquals(
            listOf("Ärger", "Bulk", "group 2", "Group 10", "Zebra", "No notifications"),
            categories.ordered().map { it.shown },
        )
    }

    private fun topic(app: String, appName: String, label: String, name: String = "", key: String = label) =
        NotifTopic(app = NotifTopicApp(id = app, name = appName), topic = key, label = label, name = name)

    /** Two apps whose names differ only in case used to be merged under one heading. */
    @Test
    fun `topics group by app, the apps in natural order of name`() {
        val topics = listOf(
            topic("w", "Wikis", "Page edited"),
            topic("f2", "feeds", "Post"),
            topic("p10", "Projects 10", "Comment"),
            topic("f1", "Feeds", "Comment"),
            topic("p2", "Projects 2", "Comment"),
        )
        val groups = topics.grouped()
        assertEquals(listOf("f2", "f1", "p2", "p10", "w"), groups.map { it.first().app.id })
        groups.forEach { group -> assertEquals(1, group.map { it.app.id }.distinct().size) }
    }

    @Test
    fun `an app's topics read by what happened, then by which thing`() {
        val topics = listOf(
            topic("p", "Projects", "Status changed", name = "Ticket 10"),
            topic("p", "Projects", "Comment", name = "Ticket 2"),
            topic("p", "Projects", "Status changed", name = "Ticket 2"),
            topic("p", "Projects", "", key = "assigned"),
            topic("p", "Projects", "Comment update", name = "Ticket 1"),
        )
        val order = topics.grouped().single().map { listOf(it.label.ifBlank { it.topic }, it.name) }
        assertEquals(
            listOf(
                listOf("assigned", ""),
                listOf("Comment", "Ticket 2"),
                listOf("Comment update", "Ticket 1"),
                listOf("Status changed", "Ticket 2"),
                listOf("Status changed", "Ticket 10"),
            ),
            order,
        )
    }

    /** A raw label key such as "notifications.topic_x" used to be shown as-is, unlike the web. */
    @Test
    fun `a raw label key falls back to the humanised topic`() {
        assertEquals(
            "Post comment new",
            topicLabel(topic("f", "Feeds", "notifications.topic_x", key = "post_comment/new")),
        )
        assertEquals("Assigned", topicLabel(topic("p", "Projects", "", key = "_assigned_")))
    }

    @Test
    fun `a real label is kept`() {
        assertEquals("Comment added", topicLabel(topic("f", "Feeds", "Comment added", key = "comment/new")))
        assertEquals("Status: done", topicLabel(topic("p", "Projects", "Status: done", key = "status")))
    }

    /** The topics used to be sorted by the raw label key rather than the name shown. */
    @Test
    fun `topics sort by the humanised topic when the label is a raw key`() {
        val topics = listOf(
            topic("p", "Projects", "aaa.topic_zulu", key = "zulu_crossing"),
            topic("p", "Projects", "Middle", key = "middle"),
            topic("p", "Projects", "zzz.topic_alpha", key = "alpha/beta"),
        )
        assertEquals(
            listOf("Alpha beta", "Middle", "Zulu crossing"),
            topics.grouped().single().map { topic -> topicLabel(topic) },
        )
    }
}
