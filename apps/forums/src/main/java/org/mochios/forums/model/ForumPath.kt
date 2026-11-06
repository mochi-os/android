// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import java.security.MessageDigest

private const val FINGERPRINT_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz"

/**
 * The segment that names a forum in a URL path. A community on another network
 * is keyed by its address, whose slashes would split the path so the request
 * reaches no route; its routes take the fingerprint the server derives from the
 * address (forum_fingerprint in forums.star). A Mochi forum's id goes as it is.
 */
fun forumPath(id: String): String {
    if (!id.contains('/')) return id
    val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
    return buildString {
        for (i in 0 until 9) append(FINGERPRINT_ALPHABET[(digest[i].toInt() and 0xff) % FINGERPRINT_ALPHABET.length])
    }
}
