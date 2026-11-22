// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.text.Spanned
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.noties.markwon.core.spans.LinkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.util.LINKED_ADDRESSES
import org.mochios.android.util.linkUri
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * A rendered body: what it links, what a touch on it does, and how a capped
 * preview ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HtmlContentTest {

    @get:Rule
    val rule = createComposeRule()

    private var view: TextView? = null

    private fun show(
        html: String,
        maxLines: Int = Int.MAX_VALUE,
        onClick: (() -> Unit)? = null,
    ): TextView {
        rule.setContent {
            Box(Modifier.width(320.dp)) {
                HtmlContent(
                    html = html,
                    // As the callers lay a body out: a table has no width of
                    // its own to wrap.
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = maxLines,
                    onClick = onClick,
                    onTextViewReady = { textView -> view = textView },
                )
            }
        }
        rule.waitForIdle()
        return drawn(view!!)
    }

    /** Draw [textView] once, as a frame on a phone would: a table lays its cells out when drawn. */
    private fun drawn(textView: TextView): TextView {
        rule.runOnIdle {
            val bitmap = Bitmap.createBitmap(textView.width.coerceAtLeast(1), textView.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            textView.draw(Canvas(bitmap))
        }
        rule.waitForIdle()
        return textView
    }

    private fun spans(textView: TextView): List<Pair<String, String>> {
        val text = textView.text as Spanned
        return text.getSpans(0, text.length, LinkSpan::class.java).map { span ->
            text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString() to span.link
        }
    }

    private fun started(): Intent? =
        shadowOf(RuntimeEnvironment.getApplication() as Application).nextStartedActivity

    /** Where [offset]'s character sits in [textView], in the view's own coordinates. */
    private fun point(textView: TextView, offset: Int): Pair<Float, Float> {
        val layout = textView.layout
        val line = layout.getLineForOffset(offset)
        val x = (layout.getPrimaryHorizontal(offset) + layout.getPrimaryHorizontal(offset + 1)) / 2
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        return x + textView.totalPaddingLeft to y + textView.totalPaddingTop
    }

    private fun touch(textView: TextView, action: Int, x: Float, y: Float, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        rule.runOnIdle { textView.dispatchTouchEvent(event) }
        event.recycle()
    }

    private fun tap(textView: TextView, x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        touch(textView, MotionEvent.ACTION_DOWN, x, y, down)
        touch(textView, MotionEvent.ACTION_UP, x, y, down)
        rule.waitForIdle()
    }

    private fun press(textView: TextView, x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        touch(textView, MotionEvent.ACTION_DOWN, x, y, down)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        touch(textView, MotionEvent.ACTION_UP, x, y, down)
        rule.waitForIdle()
    }

    @Test
    fun `web and e-mail addresses are linked, phone numbers are not`() {
        val textView = show("Write to ada@example.org, call +372 5555 1234 or see https://example.org/page")
        val links = spans(textView)
        assertTrue(links.toString(), links.contains("ada@example.org" to "mailto:ada@example.org"))
        assertTrue(links.toString(), links.any { it.second == "https://example.org/page" })
        assertTrue(links.toString(), links.none { it.first.contains("5555") || it.second.startsWith("tel:") })
    }

    @Test
    fun `every address the mask links is one the resolver opens`() {
        val sample = SpannableString("ada@example.org +372 5555 1234 https://example.org www.example.org")
        Linkify.addLinks(sample, LINKED_ADDRESSES)
        val links = sample.getSpans(0, sample.length, URLSpan::class.java).map { it.url }
        assertTrue(links.toString(), links.any { it.startsWith("mailto:") })
        assertTrue(links.toString(), links.any { it.startsWith("https:") })
        for (link in links) assertNotNull(link, linkUri(link))
    }

    @Test
    fun `a body's links open web and e-mail addresses and nothing else`() {
        assertEquals("https://example.org/a", linkUri("https://example.org/a").toString())
        assertEquals("mailto:ada@example.org", linkUri("mailto:ada@example.org").toString())
        assertNull(linkUri("tel:+37255551234"))
        assertNull(linkUri("javascript:alert(1)"))
        assertNull(linkUri("intent://x#Intent;package=evil;end"))
        assertNull(linkUri("file:///data/data/org.mochios.mochi/x"))
        assertNull(linkUri("javascript:void(0)//mailto:ada@example.org"))
    }

    @Test
    fun `a tapped e-mail address opens the mail app`() {
        val textView = show("Write to ada@example.org today")
        val (x, y) = point(textView, textView.text.indexOf("ada@") + 3)
        tap(textView, x, y)
        val intent = started()
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("mailto:ada@example.org", intent?.data.toString())
    }

    @Test
    fun `a telephone link is drawn but opens nothing`() {
        val textView = show("[call us](tel:+37255551234) now")
        val (x, y) = point(textView, 2)
        tap(textView, x, y)
        assertNull(started())
    }

    @Test
    fun `a long press on plain text selects a word`() {
        val textView = show("Plenty of plain words to choose from here")
        val (x, y) = point(textView, textView.text.indexOf("plain") + 2)
        press(textView, x, y)
        assertTrue(
            "selection ${textView.selectionStart}..${textView.selectionEnd}",
            textView.selectionEnd > textView.selectionStart,
        )
        assertEquals("plain", textView.text.subSequence(textView.selectionStart, textView.selectionEnd).toString())
    }

    @Test
    fun `a selection survives the tap that quotes it`() {
        var quoted: String? = null
        rule.setContent {
            Column(Modifier.width(320.dp)) {
                HtmlContent(
                    html = "Plenty of plain words to choose from here",
                    modifier = Modifier.fillMaxWidth(),
                    onTextViewReady = { textView -> view = textView },
                )
                Box(
                    Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .testTag("reply")
                        .clickable {
                            val textView = view!!
                            quoted = textView.text
                                .subSequence(textView.selectionStart, textView.selectionEnd)
                                .toString()
                        }
                )
            }
        }
        rule.waitForIdle()
        val textView = drawn(view!!)
        val (x, y) = point(textView, textView.text.indexOf("words") + 2)
        press(textView, x, y)
        rule.onNodeWithTag("reply").performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals("words", quoted)
    }

    @Test
    fun `a tap on plain text runs the card's handler`() {
        var cards = 0
        val textView = show("Plenty of plain words", onClick = { cards++ })
        val (x, y) = point(textView, 3)
        tap(textView, x, y)
        assertEquals(1, cards)
        assertNull(started())
    }

    @Test
    fun `a link in a table cell opens`() {
        val textView = show("| Name | Site |\n| --- | --- |\n| Ada | [home](https://example.org/ada) |")
        val layout = textView.layout
        val last = layout.lineCount - 1
        // The last row's second cell, a little past the middle of the line.
        val x = textView.totalPaddingLeft + (layout.getLineLeft(last) + layout.getLineRight(last)) * 0.6f
        val y = textView.totalPaddingTop + layout.getLineTop(last) + 8f
        tap(textView, x, y)
        val intent = started()
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("https://example.org/ada", intent?.data.toString())
    }

    /** [html] in a 320 dp box with no width modifier, as people's bio and the post previews lay a body out. */
    private fun unsized(html: String): TextView {
        rule.setContent {
            Box(Modifier.width(320.dp)) {
                HtmlContent(html = html, onTextViewReady = { textView -> view = textView })
            }
        }
        rule.waitForIdle()
        return drawn(view!!)
    }

    @Test
    fun `a body that is only a table takes the width it is offered`() {
        val textView = unsized("| Name | Site |\n| --- | --- |\n| Ada | Lovelace |")
        val offered = (320 * textView.resources.displayMetrics.density).toInt()
        assertEquals(offered, textView.width)
    }

    @Test
    fun `a short body without a table keeps its own width`() {
        val textView = unsized("Hi")
        val offered = (320 * textView.resources.displayMetrics.density).toInt()
        assertTrue("width ${textView.width} of $offered", textView.width in 1 until offered)
    }

    @Test
    fun `a link in a table cell opens, not the card around it`() {
        var cards = 0
        val textView = show(
            "| Name | Site |\n| --- | --- |\n| Ada | [home](https://example.org/ada) |",
            onClick = { cards++ },
        )
        val layout = textView.layout
        val last = layout.lineCount - 1
        val x = textView.totalPaddingLeft + (layout.getLineLeft(last) + layout.getLineRight(last)) * 0.6f
        val y = textView.totalPaddingTop + layout.getLineTop(last) + 8f
        tap(textView, x, y)
        assertEquals("https://example.org/ada", started()?.data.toString())
        assertEquals(0, cards)
    }

    @Test
    fun `a capped preview ends in an ellipsis it draws`() {
        val body = (1..40).joinToString(" ") { "word$it" }
        val textView = show(body, maxLines = 2)
        val layout = textView.layout
        val text = textView.text.toString()
        assertTrue(layout.lineCount.toString(), layout.lineCount <= 2)
        assertTrue(text, text.endsWith("…"))
        // The ellipsis is inside the lines laid out, so it is drawn.
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue(text, text.length < body.length)
    }

    @Test
    fun `a preview that fits keeps its text whole`() {
        val textView = show("A short body", maxLines = 2)
        assertEquals("A short body", textView.text.toString().trim())
    }

    @Test
    fun `a tap on a capped preview reaches the card around it`() {
        var cards = 0
        rule.setContent {
            Box(Modifier.width(320.dp).clickable { cards++ }.testTag("card")) {
                HtmlContent(
                    html = (1..40).joinToString(" ") { "word$it" },
                    maxLines = 2,
                    onTextViewReady = { textView -> view = textView },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("card").performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals(1, cards)
    }

    @Test
    fun `a drag that starts on a body scrolls the page`() {
        val scroll = ScrollState(0)
        rule.setContent {
            Column(Modifier.width(320.dp).height(300.dp).verticalScroll(scroll).testTag("page")) {
                HtmlContent(html = (1..200).joinToString(" ") { "word$it" })
                Spacer(Modifier.height(600.dp))
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("page").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue(scroll.value.toString(), scroll.value > 0)
    }
}
