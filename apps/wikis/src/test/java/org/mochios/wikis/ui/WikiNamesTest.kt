// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.format.formatFingerprint
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.fingerprint
import org.mochios.wikis.model.Change
import org.mochios.wikis.model.Revision
import org.mochios.wikis.model.RevisionDetail
import org.mochios.wikis.model.WikiComment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.ui.changes.ChangesBody
import org.mochios.wikis.ui.comments.WikiCommentThread
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.wikis.ui.history.PageHistoryBody
import org.mochios.wikis.ui.history.RevisionBody
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An author the server could not name is shown by their fingerprint, as
 * everywhere else, never by their entity id or a slice of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikiNamesTest {

    @get:Rule
    val rule = createComposeRule()

    private val wiki = WikiContextValue("w1", WikiInfo(id = "w1"), WikiPermissions(view = true), "https://server")

    /** An identity's entity id, which means nothing to a reader. */
    private val author = "1aBcDeFgHiJkLmNoPqRsTuVwXyZ23456789aBcDeFgHiJkLmNo"

    private val shown = formatFingerprint(fingerprint(author))

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalWikiContext provides wiki) { Column { content() } }
        }
        rule.waitForIdle()
    }

    /** Neither the id nor any piece of it that the screens used to cut is on screen. */
    private fun assertNoId() {
        for (piece in listOf(author, author.take(8), author.take(16))) {
            val nodes = rule.onAllNodesWithText(piece, substring = true).fetchSemanticsNodes()
            assertTrue("THE POINT: no entity id on screen ($piece): $nodes", nodes.isEmpty())
        }
    }

    @Test
    fun `a comment by an author with no name shows their fingerprint`() {
        show {
            WikiCommentThread(
                comment = WikiComment(id = "c1", author = author, name = "", body = "Body"),
                currentUserId = null, isOwner = false, onStartReply = { _, _ -> }, onEdit = null, onDelete = null,
            )
        }
        rule.onNodeWithText(shown).assertExists()
        assertNoId()
    }

    @Test
    fun `a revision by an author with no name shows their fingerprint in the history`() {
        show {
            PageHistoryBody(
                slug = "home", wikiId = "w1",
                revisions = listOf(Revision(id = "r1", author = author, name = "", version = 1)),
                currentVersion = 1, hasMore = false, load = LoadState.Idle, onLoadMore = {}, onView = {}, onRevert = {},
            )
        }
        rule.onNodeWithText(shown).assertExists()
        assertNoId()
    }

    @Test
    fun `a change by an author with no name shows their fingerprint`() {
        show {
            ChangesBody(
                changes = listOf(Change(id = "c1", slug = "home", author = author, name = "", version = 1)),
                hasMore = false, load = LoadState.Idle, onLoadMore = {}, onOpenPage = {},
            )
        }
        rule.onNodeWithText(shown).assertExists()
        assertNoId()
    }

    @Test
    fun `a revision's header names an author with no name by their fingerprint`() {
        show {
            RevisionBody(
                slug = "home",
                revision = RevisionDetail(id = "r1", version = 1, title = "Home", author = author, name = "", content = "Body"),
                currentVersion = 1, hasPrevious = false, showDiff = false, previousRevision = null,
                previousLoading = false, compareError = null,
                onToggleDiff = {}, onRevert = {}, onInternalLink = {},
            )
        }
        rule.onNodeWithText("by $shown").assertExists()
        assertNoId()
    }
}
