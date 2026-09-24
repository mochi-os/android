// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import java.util.Locale

/**
 * The tag to store and apply for the server's `language` preference, or null
 * for the device's own language. The web picker stores "auto" for that, and
 * Java parses an ill-formed tag as the root locale, which would pin the app
 * to English whatever the device says.
 */
fun languageTag(raw: String?): String? {
    val tag = raw?.trim().orEmpty()
    if (tag.isEmpty() || tag.equals("auto", ignoreCase = true)) return null
    if (Locale.forLanguageTag(tag).language.isEmpty()) return null
    return tag
}

/** What a preferences refresh does with the language: the [tag] to store, and whether to [apply] it. */
data class LanguageUpdate(val tag: String?, val apply: Boolean)

/**
 * Applying the same tag again would undo a language the user picked for the
 * app in the system settings, so only a change is applied. [previous] is what
 * the store holds, raw: a tag an older build stored unnormalised ("auto")
 * differs from its normalised form, so the root locale it pinned is released.
 */
fun languageUpdate(previous: String?, raw: String?): LanguageUpdate {
    val tag = languageTag(raw)
    return LanguageUpdate(tag, tag != previous)
}
