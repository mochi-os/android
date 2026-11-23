// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.wikis.R
import org.mochios.wikis.ui.TestWiki
import org.mochios.wikis.ui.ok
import org.mochios.wikis.ui.refused
import org.mochios.wikis.ui.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The page's tags, held by the page's own view model: a removed chip goes on
 * the tap and comes back in its place when refused, an added one joins the
 * tags shown when the server takes it, and a refusal says the server's
 * reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageTagsTest {

    /** Holds every tag write until released, so a test sees the page before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the tag writes. */
    @Volatile private var refuse = false

    /** The page's tags as the server has them. */
    @Volatile private var tags = """["alpha", "beta", "gamma"]"""

    private val wiki = TestWiki { method, path ->
        when ("$method $path") {
            "GET /wikis/w1/-/info" -> ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki"}, "permissions": {"view": true, "edit": true}}""")
            "GET /wikis/w1/-/pages/home" -> ok("""{"page": {"id": "p1", "slug": "home", "title": "Home", "content": "Text", "tags": $tags}}""")
            "POST /wikis/w1/-/pages/home/tag/remove", "POST /wikis/w1/-/pages/home/tag/add" -> {
                gate.await(5, TimeUnit.SECONDS)
                if (refuse) refused(400, "Tag refused") else ok("{}")
            }
            else -> null
        }
    }

    @Before
    fun begin() {
        wiki.start()
    }

    @After
    fun end() {
        gate.countDown()
        wiki.stop()
    }

    private fun page(): Pair<PageViewModel, MutableList<PageViewEvent>> {
        val model = PageViewModel(SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home")), wiki.repository, wiki.session, wiki.socket)
        until { model.uiState.value.page != null }
        return model to wiki.collect(model.events)
    }

    private fun PageViewModel.tags(): List<String> = uiState.value.page?.tags.orEmpty()

    @Test
    fun `a removed tag's chip goes on the tap, and back in its place with the server's reason when refused`() {
        val (model, said) = page()
        model.removeTag("beta")
        assertEquals(listOf("alpha", "gamma"), model.tags())
        until { wiki.count("POST /wikis/w1/-/pages/home/tag/remove") == 1 }

        refuse = true
        gate.countDown()
        until { said.isNotEmpty() }
        assertEquals(
            PageViewEvent.Notice(R.string.wikis_tag_manager_remove_failed, error = MochiError.Unknown("Tag refused", 400)),
            said.single(),
        )
        assertEquals(listOf("alpha", "beta", "gamma"), model.tags())
    }

    @Test
    fun `an accepted removal stays gone and says so`() {
        val (model, said) = page()
        model.removeTag("beta")
        gate.countDown()
        until { said.isNotEmpty() }
        assertEquals(PageViewEvent.Notice(R.string.wikis_tag_manager_removed, listOf("beta")), said.single())
        assertEquals(listOf("alpha", "gamma"), model.tags())
    }

    /** The tag list the add began with used to be put back over a reload that landed meanwhile. */
    @Test
    fun `an added tag joins the tags shown when the server takes it, not the ones it began with`() {
        tags = """["alpha"]"""
        val (model, said) = page()
        model.addTag("beta")
        until { wiki.count("POST /wikis/w1/-/pages/home/tag/add") == 1 }

        // Another replica's tag arrives while the add is on its way.
        tags = """["alpha", "gamma"]"""
        model.loadPage()
        until { model.tags() == listOf("alpha", "gamma") }

        gate.countDown()
        until { said.isNotEmpty() }
        assertEquals(PageViewEvent.Notice(R.string.wikis_tag_manager_added, listOf("beta")), said.single())
        assertEquals(listOf("alpha", "gamma", "beta"), model.tags())
    }

    @Test
    fun `a refused tag is not shown, and the server's reason is`() {
        tags = """["alpha"]"""
        val (model, said) = page()
        refuse = true
        gate.countDown()
        model.addTag("beta")
        until { said.isNotEmpty() }
        assertEquals(
            PageViewEvent.Notice(R.string.wikis_tag_manager_add_failed, error = MochiError.Unknown("Tag refused", 400)),
            said.single(),
        )
        assertEquals(listOf("alpha"), model.tags())
    }
}
