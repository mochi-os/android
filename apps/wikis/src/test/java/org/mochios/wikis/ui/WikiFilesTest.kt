// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui

import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.noties.markwon.core.spans.LinkSpan
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.ClickableLinkTextView
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.ui.comments.CommentAttachments
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.MarkdownContent
import org.mochios.wikis.ui.components.WikiContextValue
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * A wiki serves its files only to those who may read it, so a file opened
 * from a comment or from a page's link is fetched with the reader's
 * credential and handed to a viewer on the device; given to the browser, the
 * address answered "access denied" on every wiki not open to all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikiFilesTest {

    @get:Rule
    val rule = createComposeRule()

    private val wiki = WikiContextValue("w1", WikiInfo(id = "w1"), WikiPermissions(view = true), "https://server")

    private var root: View? = null

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            root = LocalView.current
            CompositionLocalProvider(LocalWikiContext provides wiki) {
                Box(Modifier.width(320.dp)) { content() }
            }
        }
        rule.waitForIdle()
    }

    private fun find(view: View): TextView? {
        if (view is ClickableLinkTextView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** Whether a file was fetched for a viewer, and no other app was handed an address. */
    private fun assertFetched() {
        rule.waitForIdle()
        assertTrue("THE POINT: the file is fetched for a viewer", ShadowToast.showedToast("Opening file…"))
        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertNull("no browser was handed the server's address: $started", started)
    }

    @Test
    fun `a comment's file is fetched with the reader's credential`() {
        show { CommentAttachments(attachments = listOf(Attachment(id = "a9", name = "report.pdf", type = "application/pdf"))) }
        rule.onNodeWithText("report.pdf", substring = true).performClick()
        assertFetched()
    }

    @Test
    fun `a page's link to one of the wiki's files fetches it with the reader's credential`() {
        show { MarkdownContent(content = "Read [report.pdf](attachments/a9) first") }
        val textView = find(root!!.rootView) ?: error("no text view")
        val text = textView.text as Spanned
        val span = text.getSpans(0, text.length, LinkSpan::class.java).single()
        rule.runOnUiThread { span.onClick(textView) }
        assertFetched()
    }
}
