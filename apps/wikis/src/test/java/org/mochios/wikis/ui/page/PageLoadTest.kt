// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.wikis.ui.TestWiki
import org.mochios.wikis.ui.ok
import org.mochios.wikis.ui.refused
import org.mochios.wikis.ui.settle
import org.mochios.wikis.ui.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A page whose wiki information could not be read the first time: the page's
 * Retry asks for it again, so the permissions behind Edit, Rename, Delete,
 * New page and Settings arrive, and the page follows the wiki's live updates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageLoadTest {

    /** Whether the wiki's information answers. */
    @Volatile private var available = false

    private val wiki = TestWiki { method, path ->
        when ("$method $path") {
            INFO -> if (available) {
                ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki"}, "permissions": {"view": true, "edit": true, "delete": true, "manage": true}}""")
            } else {
                refused(503, "Wiki unavailable")
            }
            PAGE -> ok("""{"page": {"id": "p1", "slug": "home", "title": "Home", "content": "Text"}}""")
            else -> null
        }
    }

    @Before
    fun begin() {
        wiki.start()
    }

    @After
    fun end() = wiki.stop()

    @Test
    fun `a failed first read of the wiki is shown, and Retry brings its permissions and live updates`() {
        val model = PageViewModel(SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home")), wiki.repository, wiki.session, wiki.socket)
        until { model.uiState.value.error != null }
        assertEquals(MochiError.ServerError(503, "Wiki unavailable"), model.uiState.value.error)
        assertNull(model.uiState.value.page)
        settle()
        assertFalse(wiki.socket.isLive("wfp"))

        available = true
        model.loadPage()
        until { model.uiState.value.page != null }
        assertEquals("THE POINT: the retry asks for the wiki again", 2, wiki.count(INFO))
        val permissions = model.uiState.value.permissions
        assertTrue(permissions.edit && permissions.delete && permissions.manage)
        assertNull(model.uiState.value.error)
        until { wiki.socket.isLive("wfp") }
    }

    @Test
    fun `a retry after the wiki was read asks only for the page`() {
        available = true
        val model = PageViewModel(SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home")), wiki.repository, wiki.session, wiki.socket)
        until { model.uiState.value.page != null }
        model.loadPage()
        until { wiki.count(PAGE) == 2 }
        settle()
        assertEquals(1, wiki.count(INFO))
    }

    private companion object {
        const val INFO = "GET /wikis/w1/-/info"
        const val PAGE = "GET /wikis/w1/-/pages/home"
    }
}
