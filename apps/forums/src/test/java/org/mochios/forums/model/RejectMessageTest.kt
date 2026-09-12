// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mochios.forums.R

class RejectMessageTest {

    @Test
    fun `each reason maps to its own message`() {
        val reasons = listOf(
            "access_denied", "restricted", "rate_limited",
            "invalid", "duplicate", "forum_not_found", "server_error",
        )
        val messages = reasons.map { rejectMessage(it, comment = false) }
        assertEquals(
            "every reason has a distinct post message",
            reasons.size,
            messages.toSet().size,
        )
    }

    @Test
    fun `post and comment wording differ for every reason but the forum itself`() {
        for (reason in listOf(
            "access_denied", "restricted", "rate_limited",
            "invalid", "duplicate", "server_error",
        )) {
            assertNotEquals(
                "$reason must not tell a commenter their post was refused",
                rejectMessage(reason, comment = false),
                rejectMessage(reason, comment = true),
            )
        }
        // The forum being gone is the one case that reads the same either way.
        assertEquals(
            rejectMessage("forum_not_found", comment = false),
            rejectMessage("forum_not_found", comment = true),
        )
    }

    @Test
    fun `an absent or unknown reason falls back to the server message`() {
        assertEquals(
            R.string.forums_reject_post_server,
            rejectMessage(null, comment = false),
        )
        assertEquals(
            R.string.forums_reject_comment_server,
            rejectMessage("some_reason_a_later_server_invents", comment = true),
        )
    }

    @Test
    fun `known reasons resolve to their own strings`() {
        assertEquals(
            R.string.forums_reject_comment_rate,
            rejectMessage("rate_limited", comment = true),
        )
        assertEquals(
            R.string.forums_reject_post_duplicate,
            rejectMessage("duplicate", comment = false),
        )
    }
}
