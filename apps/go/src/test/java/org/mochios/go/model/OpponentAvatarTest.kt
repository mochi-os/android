// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The opponent's id and the game's reach the phone from the other player's
 * server, and the image request carries the session and the token of whatever
 * app its path names.
 */
class OpponentAvatarTest {

    @Test
    fun `an opponent's avatar goes through the game's user-asset proxy`() {
        assertEquals("/go/g1/-/user/u1/asset/avatar", opponentAvatarUrl("g1", "u1"))
    }

    @Test
    fun `an id cannot point the avatar at another action`() {
        for (hostile in listOf("x/-/delete?", "..", "")) {
            assertNull(opponentAvatarUrl("g1", hostile))
            assertNull(opponentAvatarUrl(hostile, "u1"))
        }
        assertEquals("/go/g1/-/user/a%3Fb/asset/avatar", opponentAvatarUrl("g1", "a?b"))
        assertEquals("/go/a%3Fb/-/user/u1/asset/avatar", opponentAvatarUrl("a?b", "u1"))
    }
}
