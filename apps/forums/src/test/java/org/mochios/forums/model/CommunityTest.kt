// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Two communities can share a title, so their server goes beside it. */
class CommunityTest {

    @Test
    fun `a community's server comes from its handle, else its address`() {
        assertEquals("lemmy.ml", communityServer("test@lemmy.ml"))
        assertEquals("sh.itjust.works", communityServer("", "https://sh.itjust.works/c/test"))
        assertEquals("", communityServer("", "1Vg5EKW2KiMjy9xkUVmPAf2GCJcm"))
        assertEquals("!test@lemmy.ml", communityHandle("test@lemmy.ml"))
        assertEquals("", communityHandle(""))
    }

    @Test
    fun `a server shows beside a community, never beside a forum of ours`() {
        assertEquals("lemmy.ml", forumServer(Forum(id = "https://lemmy.ml/c/test", account = "l1", handle = "test@lemmy.ml")))
        assertEquals("sh.itjust.works", forumServer(Forum(id = "https://sh.itjust.works/c/test", account = "l1")))
        assertEquals("", forumServer(Forum(id = "1Vg5EKW2KiMjy9xkUVmPAf2GCJcm", address = "https://here.example/~f1")))
    }
}
