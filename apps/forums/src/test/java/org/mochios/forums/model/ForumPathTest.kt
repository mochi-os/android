// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** A community's address cannot go in a URL path; its routes take the server's fingerprint for it. */
class ForumPathTest {

    @Test
    fun `a community is named by the fingerprint the server gives it`() {
        assertEquals("mZxJ7b43h", forumPath("https://lemmy.ml/c/test"))
        assertEquals("45zHwuPUP", forumPath("https://sh.itjust.works/c/test"))
    }

    @Test
    fun `a Mochi forum id or a fingerprint goes as it is`() {
        val entity = "1Vg5EKW2KiMjy9xkUVmPAf2GCJcmX1cYKsfMG9nFbX3Bs8sEkV"
        assertEquals(entity, forumPath(entity))
        assertEquals("mZxJ7b43h", forumPath("mZxJ7b43h"))
    }
}
