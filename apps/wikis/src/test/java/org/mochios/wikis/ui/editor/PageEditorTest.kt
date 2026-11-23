// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.editor

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
import org.mochios.wikis.ui.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor's load and what it saves: a wiki that could not be read is
 * shown with the page's Retry rather than quietly dropping Delete, and a page
 * reached through a redirect is saved and deleted under its own name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageEditorTest {

    /** Whether the wiki's information answers. */
    @Volatile private var available = true

    private val wiki = TestWiki { method, path ->
        when ("$method $path") {
            "GET /wikis/w1/-/info" -> if (available) {
                ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki", "home": "home"}, "permissions": {"view": true, "edit": true, "delete": true}}""")
            } else {
                refused(503, "Wiki unavailable")
            }
            // "old" is a redirect to "home": the page served is home's.
            "GET /wikis/w1/-/pages/old", "GET /wikis/w1/-/pages/home" ->
                ok("""{"page": {"id": "p1", "slug": "home", "title": "Home", "content": "Text"}}""")
            "POST /wikis/w1/-/pages/home/edit" -> ok("""{"id": "p1", "slug": "home", "version": 2, "created": false}""")
            "POST /wikis/w1/-/pages/home/delete" -> ok("""{"ok": true, "slug": "home"}""")
            else -> null
        }
    }

    @Before
    fun begin() {
        wiki.start()
    }

    @After
    fun end() = wiki.stop()

    private fun editor(slug: String) =
        PageEditorViewModel(SavedStateHandle(mapOf("wikiId" to "w1", "page" to slug)), wiki.repository, wiki.session)

    @Test
    fun `a wiki that could not be read is shown, and Retry brings the permission to delete`() {
        available = false
        val model = editor("home")
        until { model.uiState.value.error != null }
        assertEquals(MochiError.ServerError(503, "Wiki unavailable"), model.uiState.value.error)
        assertFalse(model.uiState.value.permissions.delete)

        available = true
        model.retry()
        until { !model.uiState.value.isLoading && model.uiState.value.title == "Home" }
        assertNull(model.uiState.value.error)
        assertTrue("THE POINT: Delete is offered once the wiki is read", model.uiState.value.permissions.delete)
    }

    @Test
    fun `a page reached through a redirect is saved under its own name, and the editor goes to the page saved`() {
        val model = editor("old")
        val events = wiki.collect(model.events)
        until { model.uiState.value.title == "Home" }
        assertEquals("home", model.uiState.value.slug)

        model.setContent("Text, more")
        model.save("title", "slug", "create", "edit")
        until { events.isNotEmpty() }
        assertEquals(1, wiki.count("POST /wikis/w1/-/pages/home/edit"))
        assertEquals("THE POINT: no page is made under the redirect's name", 0, wiki.count("POST /wikis/w1/-/pages/old/edit"))
        assertEquals(PageEditorEvent.Saved("home"), events.single())
    }

    @Test
    fun `a page reached through a redirect is deleted under its own name`() {
        val model = editor("old")
        val events = wiki.collect(model.events)
        until { model.uiState.value.title == "Home" }

        model.delete("failed")
        until { events.isNotEmpty() }
        assertEquals(PageEditorEvent.Deleted, events.single())
        assertEquals(1, wiki.count("POST /wikis/w1/-/pages/home/delete"))
        assertEquals(0, wiki.count("POST /wikis/w1/-/pages/old/delete"))
    }

    @Test
    fun `a save goes to the page the server says it saved`() {
        val answered = TestWiki { method, path ->
            when ("$method $path") {
                "GET /wikis/w1/-/info" -> ok("""{"wiki": {"id": "w1", "name": "Wiki"}, "permissions": {"edit": true}}""")
                "GET /wikis/w1/-/pages/home" -> ok("""{"page": {"id": "p1", "slug": "home", "title": "Home", "content": "Text"}}""")
                "POST /wikis/w1/-/pages/home/edit" -> ok("""{"id": "p1", "slug": "moved", "version": 2, "created": false}""")
                else -> null
            }
        }.start()
        try {
            val model = PageEditorViewModel(SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home")), answered.repository, answered.session)
            val events = answered.collect(model.events)
            until { model.uiState.value.title == "Home" }
            model.save("title", "slug", "create", "edit")
            until { events.isNotEmpty() }
            assertEquals(PageEditorEvent.Saved("moved"), events.single())
        } finally {
            answered.stop()
        }
    }
}
