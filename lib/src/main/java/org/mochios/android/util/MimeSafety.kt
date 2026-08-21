// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/** What an attachment is given as its type when we will not honour the stated one. */
const val OPAQUE_MIME = "application/octet-stream"

/**
 * Types we are willing to hand to `ACTION_VIEW` for a peer-supplied file.
 *
 * An allowlist rather than a blocklist: the set of handlers that will act on a
 * type is open-ended and platform-defined, so enumerating the dangerous ones is
 * a race we lose. Anything outside this becomes [OPAQUE_MIME], which still
 * opens a chooser for genuine documents but matches no installer.
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
 * Reduce [candidate] to a type that is safe to view.
 *
 * The MIME of an attachment is attacker-controlled end to end: the server's
 * attachments library reads it from the multipart `Content-Type` header with
 * no allowlist, and its `attachment_store` copies it from the peer
 * length-bounded but type-unvalidated, so a file
 * shared into a chat, forum, feed, project or CRM board arrives carrying
 * whatever type its sender chose. Handed straight to `ACTION_VIEW`,
 * `application/vnd.android.package-archive` resolves to the system package
 * installer — and the app holds REQUEST_INSTALL_PACKAGES and has already
 * walked the user through granting the matching consent for its own updater.
 *
 * Applied to the filename-inferred type as well as the server's, because a
 * peer who leaves the type blank and names the file `x.apk` reaches the same
 * place by the other branch.
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
