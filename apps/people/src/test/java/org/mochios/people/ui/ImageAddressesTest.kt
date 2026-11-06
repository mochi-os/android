// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.people.model.FriendInvite
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.User
import org.mochios.people.ui.invitations.inviteAvatar
import org.mochios.people.ui.profile.avatarPath
import org.mochios.people.ui.profile.imageUrl
import org.mochios.people.ui.contacts.avatarUrlFor as contactAvatar
import org.mochios.people.ui.contacts.bannerUrlFor as contactBanner
import org.mochios.people.ui.person.avatarUrlFor as personAvatar
import org.mochios.people.ui.person.bannerUrlFor as personBanner

/**
 * A person's id and image versions reach the phone from their server, and an
 * image request carries the session and the token of whatever app its path
 * names. An id no segment can carry has no image; a version none can carry is
 * left off the address.
 */
class ImageAddressesTest {

    private val hostile = listOf("x/-/delete?", "..", ".")

    @Test
    fun `an invitation's avatar is the person's own`() {
        assertEquals("/people/p1/-/avatar", inviteAvatar(FriendInvite(id = "p1")))
        assertEquals("/people/a%3Fb/-/avatar", inviteAvatar(FriendInvite(id = "a?b")))
        for (id in hostile) assertNull(inviteAvatar(FriendInvite(id = id)))
    }

    @Test
    fun `a person's avatar and banner carry their versions`() {
        val info = PersonInformation(id = "p1", avatar = "v1", banner = "v2")
        assertEquals("/people/p1/-/avatar?v=v1", personAvatar(info))
        assertEquals("/people/p1/-/banner?v=v2", personBanner(info))
        assertEquals("/people/p1/-/avatar?v=v1", contactAvatar(info, User()))
        assertEquals("/people/p1/-/banner?v=v2", contactBanner(info, User()))
        assertEquals("/people/p1/-/avatar", contactAvatar(PersonInformation(), User(id = "p1")))
        assertEquals("/people/p1/-/avatar?v=v1", avatarPath("p1", "v1"))
        assertEquals("/people/p1/-/banner?v=v2", imageUrl("p1", "banner", "v2"))
    }

    @Test
    fun `a person's id cannot point an image at another action`() {
        for (id in hostile) {
            val info = PersonInformation(id = id, avatar = "v1", banner = "v2")
            assertNull(personAvatar(info))
            assertNull(personBanner(info))
            assertNull(contactAvatar(info, User()))
            assertNull(contactBanner(info, User()))
            assertNull(contactAvatar(PersonInformation(), User(id = id)))
            assertNull(avatarPath(id, "v1"))
            assertNull(imageUrl(id, "banner", "v2"))
        }
        val info = PersonInformation(id = "a?b", avatar = "v1", banner = "v2")
        assertEquals("/people/a%3Fb/-/avatar?v=v1", personAvatar(info))
        assertEquals("/people/a%3Fb/-/banner?v=v2", contactBanner(info, User()))
        assertEquals("/people/a%3Fb/-/favicon?v=v2", imageUrl("a?b", "favicon", "v2"))
    }

    @Test
    fun `a version is escaped, and one no segment can carry is left off`() {
        val info = PersonInformation(id = "p1", avatar = "x/-/delete?", banner = "a?b&c")
        assertEquals("/people/p1/-/avatar", personAvatar(info))
        assertEquals("/people/p1/-/banner?v=a%3Fb%26c", personBanner(info))
        assertEquals("/people/p1/-/avatar", contactAvatar(info, User()))
        assertEquals("/people/p1/-/banner?v=a%3Fb%26c", contactBanner(info, User()))
        assertEquals("/people/p1/-/avatar", avatarPath("p1", ".."))
        assertEquals("/people/p1/-/banner?v=a%3Fb%26c", imageUrl("p1", "banner", "a?b&c"))
    }
}
