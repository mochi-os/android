// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import java.text.Normalizer

/** Everything an address may not hold, however long the run. */
private val NotSlug = Regex("[^a-z0-9]+")

/** The accents NFKD splits off a letter, once the letter itself is safe. */
private val Combining = Regex("\\p{InCombiningDiacriticalMarks}+")

/**
 * "Café" as "cafe" rather than "caf" - an accent belongs to the letter it sits
 * on, so folding it keeps the word instead of cutting it in two.
 *
 * NFKD only separates accents that compose: a letter carrying its mark inside
 * the glyph, like "ø" or "đ", still has nothing ASCII to fall back to and drops
 * out with everything else.
 */
private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Combining, "")

/**
 * Text as the address it will be reached by: lower case, digits and hyphens,
 * nothing else. A wiki page derives one from its title, a project from its
 * name, and both mean the same thing by it.
 *
 * [maxLength] caps the result where the server does - a project prefix, say -
 * trimming again afterwards so the cut never lands on a hyphen.
 *
 * Not for markdown heading anchors: [org.mochios.wikis] keeps its own
 * `slugifyHeading`, which folds diacritics and has to agree with web's,
 * character for character.
 */
fun slugify(text: String, maxLength: Int? = null): String {
    val slug = fold(text).lowercase().replace(NotSlug, "-").trim('-')
    return if (maxLength == null) slug else slug.take(maxLength).trimEnd('-')
}

/**
 * [slugify] for a field the user is still typing in: a trailing hyphen
 * survives, because it is a word separator they have not finished using yet.
 * Trimming it on every keystroke makes "my-page" impossible to type.
 */
fun slugifyPartial(text: String): String =
    fold(text).lowercase().replace(NotSlug, "-").trimStart('-')
