// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.forums.model.CommunitiesImported
import org.mochios.forums.model.CommunityFound
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What the Find screen makes of another network's answers: a community's avatar, and what an import read in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommunityAnswersTest {

    private val resources = RuntimeEnvironment.getApplication().resources
    private val format = Format(UserPreferences())

    @Test
    fun `a looked-up community's avatar is fetched only from a web address`() {
        val icon = "https://lemmy.world/pictrs/image/icon.png"
        assertEquals(icon, communityAvatar(CommunityFound(id = "c1", avatar = icon)))
        // THE POINT: an address of this server's own would be fetched as one
        // of the app's routes.
        assertNull(communityAvatar(CommunityFound(id = "c1", avatar = "/forums/f1/-/delete")))
        assertNull(communityAvatar(CommunityFound(id = "c1", avatar = "mochi://peer/f1")))
        assertNull(communityAvatar(CommunityFound(id = "c1", avatar = "")))
    }

    @Test
    fun `an import says each count in its own plural, joined as a list`() {
        // Joined as the language joins a list, which says where its commas go.
        val one = format.formatList(listOf("1 community", "2 blocks", "1 saved post"))
        assertEquals(
            "Imported $one",
            importedMessage(resources, format, CommunitiesImported(communities = 1, blocks = 2, saved = 1)),
        )
        val more = format.formatList(listOf("3 communities", "1 block", "0 saved posts"))
        assertEquals(
            "Imported $more so far; the rest follow",
            importedMessage(resources, format, CommunitiesImported(communities = 3, blocks = 1, saved = 0, more = true)),
        )
    }
}
