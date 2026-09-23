// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether an event description holds markup that should render rather than print. */
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
}
