// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether an event description holds markup, and the text it reads as where only text can go. */
class HtmlTest {

    @Test
    fun `a tag with a real name is markup`() {
        assertTrue(isHtml("PNR: 2YHEIJ <br>Class: Business"))
        assertTrue(isHtml("<span style=\"font-family: x\">Ref</span>"))
        assertTrue(isHtml("one</p>"))
        assertTrue(isHtml("<BR/>"))
    }

    @Test
    fun `plain text is not, angle brackets and all`() {
        for (text in listOf("a < b", "<3 you", "plain\ntext", "<unknown>", "", "x > y")) {
            assertFalse(text, isHtml(text))
        }
    }

    @Test
    fun `breaks and block ends become newlines and other tags drop`() {
        assertEquals("PNR: 2YHEIJ\nClass: Business & lounge", textFromHtml("PNR: 2YHEIJ<br>Class: <b>Business</b> &amp; lounge"))
        assertEquals("One\n\nTwo", textFromHtml("<p>One</p>\n<p>Two</p>"))
        assertEquals("a\nb\nc", textFromHtml("<ul><li>a</li><li>b</li><LI>c</LI></ul>"))
        assertEquals("Join\n\nNotes", textFromHtml("<a href=\"https://meet.example/x\">Join</a><br><br/><BR /><br>Notes"))
    }

    @Test
    fun `scripts, styles and comments vanish`() {
        assertEquals("Hi", textFromHtml("<style>p { color: red }</style><p>Hi</p><!-- a <b>note</b> --><script>alert(1)</script>"))
    }

    @Test
    fun `entities decode, and what they spell is text rather than markup`() {
        assertEquals("<b> 'q' \u2019  x", textFromHtml("<p>&lt;b&gt; &#39;q&#39; &#x2019; &nbsp;x</p>"))
        assertEquals("&unknown; stays", textFromHtml("<span>&unknown; stays</span>"))
        assertEquals("a < b", textFromHtml("<p>a < b</p>"))
    }

    @Test
    fun `a plain description is left as it is`() {
        for (text in listOf("a < b", "  indented\n\n\n\nand spaced  ", "Tea &amp; cake")) {
            assertEquals(text, descriptionText(text))
        }
        assertEquals("Tea & cake", descriptionText("<b>Tea</b> &amp; cake"))
    }
}
