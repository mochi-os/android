// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Photo and user ids reach the phone from other servers, and an image request
 * carries the session and the token of whatever app its path names.
 */
class AssetAddressesTest {

    @Test
    fun `a photo is the market's photo route, with the variant after it`() {
        assertEquals("/market/-/photo/p1", photoAddress("p1"))
        assertEquals("/market/-/photo/p1/thumbnail", photoAddress("p1", "thumbnail"))
        assertEquals("/market/-/photo/owned/p1/thumbnail", photoAddress("p1", "thumbnail", owned = true))
    }

    @Test
    fun `a photo id cannot point the address at another action`() {
        for (hostile in listOf("x/-/delete?", "..", "")) {
            assertNull(photoAddress(hostile))
            assertNull(photoAddress(hostile, "thumbnail"))
            assertNull(photoAddress(hostile, "thumbnail", owned = true))
        }
        assertEquals("/market/-/photo/a%3Fb/thumbnail", photoAddress("a?b", "thumbnail"))
    }

    @Test
    fun `a user's avatar goes through the market's own proxy`() {
        assertEquals("/market/-/user/u1/asset/avatar", userAvatarUrl("u1"))
    }

    @Test
    fun `a user id cannot point the avatar at another action`() {
        assertNull(userAvatarUrl("x/-/delete?"))
        assertNull(userAvatarUrl(".."))
        assertNull(userAvatarUrl(""))
        assertEquals("/market/-/user/a%3Fb/asset/avatar", userAvatarUrl("a?b"))
    }
}
