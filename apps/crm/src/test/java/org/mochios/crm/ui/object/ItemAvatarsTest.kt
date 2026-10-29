// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.model.Comment
import org.mochios.crm.model.Activity

/**
 * A person's avatar route names one of their comments or activity rows, so an
 * item that built each row's own address had the image cache fetch and store
 * the same picture once per row: one address per person instead.
 */
class ItemAvatarsTest {
    private fun by(id: String, author: String, vararg children: Comment) =
        Comment(id = id, author = author, children = children.toList())

    @Test
    fun `each commenter gets their first comment's address, replies included`() {
        val comments = listOf(by("c1", "bob", by("c2", "ann"), by("c3", "bob")), by("c4", "ann"))
        assertEquals(
            mapOf("bob" to "/crm/x1/-/comment/c1/asset/avatar", "ann" to "/crm/x1/-/comment/c2/asset/avatar"),
            commentAvatars(comments, "x1"),
        )
    }

    @Test
    fun `each person on the activity tab gets their first row's address`() {
        val activity = listOf(
            Activity(id = "a1", user = "ann"),
            Activity(id = "a2", user = "bob"),
            Activity(id = "a3", user = "ann"),
            Activity(id = "a4", user = ""),
        )
        assertEquals(
            mapOf("ann" to "/crm/x1/-/activity/a1/asset/avatar", "bob" to "/crm/x1/-/activity/a2/asset/avatar"),
            activityAvatars(activity, "x1"),
        )
    }
}
