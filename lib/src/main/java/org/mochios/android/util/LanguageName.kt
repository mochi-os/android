// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import java.util.Locale

/**
 * Where the platform's own name for a language is not Mochi's wording, as the
 * web's language picker overrides it (lib/web language-picker.tsx): English
 * without a region is neither UK nor US English, and three languages have no
 * name of their own in the platform's data.
 */
private val LANGUAGE_NAMES = mapOf(
    "en" to "English (international)",
    "en-us" to "English (USA)",
    "es" to "Español (España)",
    "es-419" to "Español (latinoamericano)",
    "ay" to "Aymar aru",
    "gn" to "Avañe'ẽ",
    "ht" to "Kreyòl ayisyen",
)

/**
 * A language in its own name, so a reader finds theirs whatever language the
 * app is in. A tag the platform cannot name is shown as it is.
 */
fun languageName(tag: String): String {
    LANGUAGE_NAMES[tag.lowercase()]?.let { return it }
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayName(locale)
    if (name.isNullOrBlank() || name == tag) return tag
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/** Each of [tags] once, with its own name, in the order a reader looks for theirs ([LanguageCompare]). */
fun languageChoices(tags: List<String>): List<Pair<String, String>> =
    tags.distinct().map { it to languageName(it) }.sortedWith(compareBy(LanguageCompare) { it.second })
