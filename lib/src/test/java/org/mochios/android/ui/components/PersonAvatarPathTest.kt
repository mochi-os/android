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
        assertEquals("/people/p1/-/avatar", personAvatarPath("p1")?.address)
    }

    @Test
    fun `an id cannot point the avatar at another action`() {
        assertNull(personAvatarPath("x/-/delete?"))
        assertNull(personAvatarPath(".."))
        assertNull(personAvatarPath("."))
        assertEquals("/people/a%3Fb/-/avatar", personAvatarPath("a?b")?.address)
    }

    @Test
    fun `a person's image carries its version, escaped`() {
        assertEquals("/people/p1/-/banner?v=v2", personAsset("p1", "banner", "v2")?.address)
        assertEquals("/people/p1/-/banner?v=a%3Fb%26c", personAsset("p1", "banner", "a?b&c")?.address)
        assertEquals("/people/a%3Fb/-/favicon?v=v2", personAsset("a?b", "favicon", "v2")?.address)
        // A version no segment can carry is left off; none is no version.
        assertEquals("/people/p1/-/avatar", personAsset("p1", "avatar", "x/-/delete?")?.address)
        assertEquals("/people/p1/-/avatar", personAsset("p1", "avatar", "..")?.address)
        assertEquals("/people/p1/-/avatar", personAsset("p1", "avatar", "")?.address)
        assertEquals("/people/p1/-/avatar", personAsset("p1", "avatar")?.address)
    }

    @Test
    fun `an id cannot point an image at another action`() {
        for (id in listOf("x/-/delete?", "..", ".", "", "  ", null)) {
            assertNull(personAsset(id, "banner", "v1"))
        }
    }

    @Test
    fun `no id has no avatar`() {
        assertNull(personAvatarPath(null))
        assertNull(personAvatarPath(""))
        assertNull(personAvatarPath("  "))
    }
}
