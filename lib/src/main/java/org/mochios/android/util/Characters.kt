// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * The first [maximum] characters of [text], counted as the server counts a
 * limit a person types against: by code point, so an emoji is one character
 * and is never cut in half, where `take` counts UTF-16 units.
 */
fun characters(text: String, maximum: Int): String =
    if (text.codePointCount(0, text.length) <= maximum) text
    else text.substring(0, text.offsetByCodePoints(0, maximum))
