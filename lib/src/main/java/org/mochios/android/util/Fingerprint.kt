// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import java.security.MessageDigest

/** The letters core writes an entity's fingerprint in. */
private const val LETTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz"

/**
 * The nine-letter fingerprint core derives from an entity [id], what a person
 * is shown as when no name is known for them: never the id itself. An [id]
 * that is already a fingerprint is its own; a blank one has none ("").
 */
fun fingerprint(id: String): String {
    if (id.isBlank()) return ""
    if (isFingerprint(id)) return id
    val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
    return String(CharArray(9) { index -> LETTERS[(hash[index].toInt() and 0xff) % LETTERS.length] })
}

/** Whether [text] is a fingerprint rather than an entity id. */
fun isFingerprint(text: String): Boolean =
    text.length == 9 && text.all { letter -> letter in LETTERS }
