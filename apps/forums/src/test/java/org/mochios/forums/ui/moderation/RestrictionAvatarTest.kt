// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.moderation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A restricted user's id, and in a remote view the forum's, reach the phone
 * from another server, and the image request carries the session and the
 * token of whatever app its path names.
 */
class RestrictionAvatarTest {

    @Test
    fun `a restricted user's avatar goes through the forum's moderation proxy`() {
        assertEquals("/forums/f1/-/moderation/u1/asset/avatar", restrictionAvatar("f1", "u1"))
    }

    @Test
    fun `a user's id cannot point the avatar at another action`() {
        assertNull(restrictionAvatar("f1", "x/-/delete?"))
        assertNull(restrictionAvatar("f1", ".."))
        assertNull(restrictionAvatar("f1", ""))
        assertEquals("/forums/f1/-/moderation/a%3Fb/asset/avatar", restrictionAvatar("f1", "a?b"))
    }

    @Test
    fun `a forum's id cannot point the avatar at another action`() {
        assertNull(restrictionAvatar("x/-/delete?", "u1"))
        assertNull(restrictionAvatar("..", "u1"))
        assertEquals("/forums/a%3Fb/-/moderation/u1/asset/avatar", restrictionAvatar("a?b", "u1"))
    }
}
