// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The image request for the user's avatar carries the session and the token of
 * whatever app its path names, so the identity is escaped as one segment, and
 * one no segment can carry has no avatar.
 */
class HomeAvatarTest {

    @Test
    fun `the user's avatar is the people app's route`() {
        assertEquals("/people/p1/-/avatar", avatar("p1"))
    }

    @Test
    fun `an identity cannot point the avatar at another action`() {
        assertNull(avatar("x/-/delete?"))
        assertNull(avatar(".."))
        assertNull(avatar(""))
        assertEquals("/people/a%3Fb/-/avatar", avatar("a?b"))
    }
}
