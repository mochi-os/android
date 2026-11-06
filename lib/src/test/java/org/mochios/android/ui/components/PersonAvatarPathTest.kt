// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A person's id reaches the phone from their server, and an image request
 * carries the session and the token of whatever app its path names: the id is
 * escaped as one segment, and one no segment can carry has no address.
 */
class PersonAvatarPathTest {

    @Test
    fun `a person's avatar is the people app's route`() {
        assertEquals("/people/p1/-/avatar", personAvatarPath("p1"))
    }

    @Test
    fun `an id cannot point the avatar at another action`() {
        assertNull(personAvatarPath("x/-/delete?"))
        assertNull(personAvatarPath(".."))
        assertNull(personAvatarPath("."))
        assertEquals("/people/a%3Fb/-/avatar", personAvatarPath("a?b"))
    }

    @Test
    fun `no id has no avatar`() {
        assertNull(personAvatarPath(null))
        assertNull(personAvatarPath(""))
        assertNull(personAvatarPath("  "))
    }
}
