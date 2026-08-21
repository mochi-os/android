// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/** What an attachment is given as its type when we will not honour the stated one. */
const val OPAQUE_MIME = "application/octet-stream"

/**
 * Types we will hand to `ACTION_VIEW` for a peer-supplied file. An allowlist:
 * the set of handlers that act on a type is open-ended, so enumerating the
 * dangerous ones cannot be complete.
 */
private val SAFE_PREFIXES = listOf("image/", "video/", "audio/", "text/")

private val SAFE_TYPES = setOf(
    "application/pdf",
    "application/json",
    "application/xml",
    "application/zip",
    "application/rtf",
    "application/epub+zip",
    "application/msword",
    "application/vnd.ms-excel",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.oasis.opendocument.spreadsheet",
    "application/vnd.oasis.opendocument.presentation",
)

/**
 * Reduce [candidate] to a type safe to view - the server's and the
 * filename-inferred type alike. Attachment MIME is peer-supplied, and with
 * REQUEST_INSTALL_PACKAGES held an APK type reaching ACTION_VIEW opens the
 * installer.
 */
fun coerceMimeType(candidate: String): String {
    // Strip any parameters ("text/plain; charset=utf-8") and normalise before
    // matching, so a type cannot slip through on case or a trailing directive.
    val type = candidate.substringBefore(';').trim().lowercase()
    if (type.isEmpty()) return OPAQUE_MIME
    // A wildcard would let the chooser offer every handler, installer included.
    if (type.contains('*')) return OPAQUE_MIME
    if (type in SAFE_TYPES) return type
    if (SAFE_PREFIXES.any { type.startsWith(it) }) return type
    return OPAQUE_MIME
}
