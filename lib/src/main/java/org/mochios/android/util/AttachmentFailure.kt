// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import coil3.network.HttpException

/**
 * Why an attachment's bytes failed to load, judged from the server's answer.
 *
 * [UNAVAILABLE] means the bytes may exist but cannot be served right now: the
 * server said 502/503/504 (its source host is unreachable - it retries after a
 * backoff), or the server itself could not be reached. A retry later may
 * simply work. Everything else - 404 above all - is [MISSING]: the server
 * answered, and the bytes are not to be had. Mirrors the web gallery's
 * classifyAttachmentFailure, and the server side's attachment_serve split
 * between attachment.errors.unavailable and not_found.
 */
enum class AttachmentFailure { UNAVAILABLE, MISSING }

/** The class a load failure with this HTTP status belongs to; null status means the server was never reached. */
fun attachmentFailure(status: Int?): AttachmentFailure = when (status) {
    null, 502, 503, 504 -> AttachmentFailure.UNAVAILABLE
    else -> AttachmentFailure.MISSING
}

/** The HTTP status a Coil load failure carries, or null when it never got one. */
fun attachmentStatus(throwable: Throwable?): Int? =
    (throwable as? HttpException)?.response?.code
