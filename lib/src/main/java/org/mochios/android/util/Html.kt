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

private val BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val BLOCK = Regex("</(blockquote|div|h[1-6]|li|p|pre|table|tr)\\s*>", RegexOption.IGNORE_CASE)
private val HIDDEN = Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

// Any tag an HTML parser would read as one: a name after "<" or "</", or a
// declaration. A "<" before a space or a digit is text, as the browser has it.
private val MARKUP = Regex("<(/?[a-zA-Z][^>]*|![^>]*|\\?[^>]*)>")
private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")

private val ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "ndash" to "–", "mdash" to "—", "hellip" to "…", "bull" to "•", "middot" to "·",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
    "laquo" to "«", "raquo" to "»", "copy" to "©", "reg" to "®", "trade" to "™",
    "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢", "deg" to "°",
    "times" to "×", "divide" to "÷", "plusmn" to "±", "sect" to "§", "para" to "¶",
    "iexcl" to "¡", "iquest" to "¿", "shy" to "­", "zwnj" to "‌", "zwj" to "‍",
)

/** Whether text holds HTML markup, as a subscribed Google calendar's event descriptions do. */
fun isHtml(text: String): Boolean = TAG.containsMatchIn(text)

/**
 * The text of an HTML fragment: line breaks and block ends become newlines,
 * every other tag drops, entities decode, styles and scripts vanish, and
 * runs of blank lines collapse to one. The web's textFromHtml() reads the
 * same way; this one needs no HTML parser, so it runs in a unit test.
 */
fun textFromHtml(html: String): String {
    val marked = html.replace(BREAK, "\n").replace(BLOCK, "\n")
    val stripped = marked.replace(HIDDEN, "").replace(COMMENT, "").replace(MARKUP, "")
    return stripped.replace(ENTITY) { match -> entity(match.groupValues[1]) ?: match.value }
        .replace(' ', ' ')
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n[ \\t]+"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

/** The text of a description, whether it came as HTML or as plain text. */
fun descriptionText(description: String): String =
    if (isHtml(description)) textFromHtml(description) else description

/** The character an entity's name or number stands for, or null for one it does not know. */
private fun entity(name: String): String? {
    val code = when {
        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)
        name.startsWith("#") -> name.substring(1).toIntOrNull()
        else -> return ENTITIES[name]
    } ?: return null
    return if (Character.isValidCodePoint(code) && code != 0) String(Character.toChars(code)) else "�"
}
