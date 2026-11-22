// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import android.app.Application
import android.content.Intent
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.noties.markwon.core.spans.LinkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.ui.components.ClickableLinkTextView
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What a wiki page links in its text, and where a tap on each link goes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarkdownLinksTest {

    @get:Rule
    val rule = createComposeRule()

    private val wiki = WikiContextValue("w1", WikiInfo(id = "w1"), WikiPermissions(view = true), "https://server")

    private fun page(content: String): TextView {
        var root: View? = null
        rule.setContent {
            root = LocalView.current
            CompositionLocalProvider(LocalWikiContext provides wiki) {
                Box(Modifier.width(320.dp)) {
                    MarkdownContent(content = content)
                }
            }
        }
        rule.waitForIdle()
        return find(root!!.rootView) ?: error("no text view")
    }

    private fun find(view: View): TextView? {
        if (view is ClickableLinkTextView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun links(textView: TextView): Map<String, LinkSpan> {
        val text = textView.text as Spanned
        return text.getSpans(0, text.length, LinkSpan::class.java).associateBy { span ->
            text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
        }
    }

    private fun started(): Intent? =
        shadowOf(RuntimeEnvironment.getApplication() as Application).nextStartedActivity

    @Test
    fun `web and e-mail addresses are linked, phone numbers are not`() {
        val links = links(page("Write to ada@example.org, call +372 5555 1234 or see https://example.org/page"))
        assertEquals("mailto:ada@example.org", links["ada@example.org"]?.link)
        assertEquals("https://example.org/page", links["https://example.org/page"]?.link)
        assertTrue(links.keys.toString(), links.keys.none { it.contains("5555") })
    }

    @Test
    fun `a tapped e-mail address opens the mail app`() {
        val textView = page("Write to ada@example.org today")
        links(textView).getValue("ada@example.org").onClick(textView)
        val intent = started()
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("mailto:ada@example.org", intent?.data.toString())
    }

    @Test
    fun `a telephone link opens nothing`() {
        val textView = page("[call us](tel:+37255551234) now")
        links(textView).getValue("call us").onClick(textView)
        assertNull(started())
    }
}
