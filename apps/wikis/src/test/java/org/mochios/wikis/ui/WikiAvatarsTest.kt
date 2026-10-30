// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.wikis.model.Change
import org.mochios.wikis.model.Revision
import org.mochios.wikis.model.WikiComment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.ui.changes.ChangesBody
import org.mochios.wikis.ui.comments.WikiCommentThread
import org.mochios.wikis.ui.comments.threadAvatars
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.wikis.ui.history.PageHistoryBody
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * An author's avatar route names one of their revisions or comments, so a list
 * that named each row's own had the image cache fetch and store the same
 * picture once per row.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class WikiAvatarsTest {

    @get:Rule
    val rule = createComposeRule()

    private val loaded = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    loaded.add(chain.request.data.toString())
                    ErrorResult(null, chain.request, IllegalStateException("no images in tests"))
                })
            }.build()
        )
    }

    @After
    fun end() = SingletonImageLoader.reset()

    private fun inWiki(content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(
                LocalWikiContext provides WikiContextValue("w1", WikiInfo(id = "w1"), WikiPermissions(view = true), "https://server"),
            ) {
                content()
            }
        }
    }

    private fun settled(rows: Int): List<String> {
        rule.waitUntil(5_000) { loaded.size >= rows }
        rule.waitForIdle()
        return loaded.toSet().sorted()
    }

    private val authors = listOf("ann", "bob", "ann", "bob")

    @Test
    fun `page history asks for each author's avatar once`() {
        val revisions = authors.mapIndexed { n, author -> Revision(id = "r$n", author = author, name = author, version = n + 1) }
        inWiki {
            PageHistoryBody(
                slug = "home", wikiId = "w1", revisions = revisions, currentVersion = 4,
                hasMore = false, isLoadingMore = false, onLoadMore = {}, onView = {}, onRevert = {},
            )
        }
        assertEquals(
            listOf("https://server/wikis/w1/-/revision/r0/asset/avatar", "https://server/wikis/w1/-/revision/r1/asset/avatar"),
            settled(revisions.size),
        )
    }

    @Test
    fun `recent changes ask for each author's avatar once`() {
        val changes = authors.mapIndexed { n, author -> Change(id = "r$n", slug = "home", author = author, name = author, version = n + 1) }
        inWiki {
            ChangesBody(changes = changes, hasMore = false, isLoadingMore = false, onLoadMore = {}, onOpenPage = {})
        }
        assertEquals(
            listOf("https://server/wikis/w1/-/revision/r0/asset/avatar", "https://server/wikis/w1/-/revision/r1/asset/avatar"),
            settled(changes.size),
        )
    }

    @Test
    fun `a comment thread asks for each author's avatar once, replies included`() {
        fun comment(id: String, author: String, vararg children: WikiComment) =
            WikiComment(id = id, author = author, name = author, body = "Body $id", children = children.toList())
        val root = comment("c1", "bob", comment("c2", "ann"), comment("c3", "bob", comment("c4", "ann")))
        inWiki {
            WikiCommentThread(
                comment = root, currentUserId = null, isOwner = false,
                onStartReply = { _, _ -> }, onEdit = null, onDelete = null,
                avatars = threadAvatars(listOf(root)),
            )
        }
        assertEquals(
            listOf("/wikis/w1/-/comment/c1/asset/avatar", "/wikis/w1/-/comment/c2/asset/avatar"),
            settled(4),
        )
    }
}
