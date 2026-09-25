// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

// A tag with a real name, opening or closing, with attributes or not. "a < b"
// and "<3" are not markup, and neither is an unknown angle-bracketed word. The
// web's isHtml() reads the same shape.
private val TAG = Regex(
    "</?(a|b|blockquote|br|code|div|em|font|h[1-6]|hr|i|img|li|ol|p|pre|small|span|strong|sub|sup|table|td|th|tr|u|ul)\\b[^>]*>",
    RegexOption.IGNORE_CASE,
)

/** Whether text holds HTML markup, as a subscribed Google calendar's event descriptions do. */
fun isHtml(text: String): Boolean = TAG.containsMatchIn(text)
