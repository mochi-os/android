// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.router

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stored section token. An install that last saw the friends list writes
 * "friends"; after the upgrade that token has to land on contacts rather than
 * on an unknown section.
 */
class PeopleSectionTest {

    @Test
    fun `the token written before contacts replaced friends resolves to contacts`() {
        assertEquals(PeopleSection.CONTACTS, peopleSection("friends"))
    }

    @Test
    fun `every current token resolves to itself`() {
        for (section in listOf(
            PeopleSection.CONTACTS,
            PeopleSection.INVITATIONS,
            PeopleSection.GROUPS,
            PeopleSection.PROFILE,
        )) {
            assertEquals(section, peopleSection(section))
        }
    }

    @Test
    fun `no stored token stays empty, and the router falls through to contacts`() {
        assertEquals("", peopleSection(""))
    }
}
