// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mochios.android.api.MochiError
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.api.PostDetailResponse

/**
 * `action_view` answers a deleted or unknown post with 200 and an empty
 * `posts` array, not a 404, so the repository has to raise the not-found
 * state itself for the detail screen's NotFoundState branch to engage.
 */
class PostNotFoundTest {

    @Test
    fun `an empty posts array is a not-found, not a generic failure`() {
        try {
            postDetail(PostDetailResponse())
            fail("expected a not-found")
        } catch (e: Exception) {
            assertTrue(
                "expected NotFoundError, got ${e.javaClass.name}",
                e is MochiError.NotFoundError,
            )
        }
    }

    @Test
    fun `a present post is returned with its permissions`() {
        val result = postDetail(
            PostDetailResponse(
                posts = listOf(Post(id = "post1")),
                permissions = Permissions(comment = true),
            )
        )
        assertEquals("post1", result.post.id)
        assertTrue(result.permissions.comment)
    }
}
