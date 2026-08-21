// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The failure classes mirror the server's attachment_serve split and the web
 * gallery's classifyAttachmentFailure: 502/503/504 or no status at all (the
 * server was never reached, or the transfer broke off) mean the bytes may
 * exist and a retry may work; any other answer is a verdict that they are not
 * to be had. Drift here and the three layers stop telling the same story.
 */
class AttachmentFailureTest {

    @Test
    fun unreachable_gateway_statuses_are_unavailable() {
        for (status in intArrayOf(502, 503, 504)) {
            assertEquals(AttachmentFailure.UNAVAILABLE, attachmentFailure(status))
        }
    }

    @Test
    fun no_status_is_unavailable_not_a_verdict() {
        assertEquals(AttachmentFailure.UNAVAILABLE, attachmentFailure(null))
    }

    @Test
    fun an_answered_failure_is_missing() {
        for (status in intArrayOf(400, 401, 403, 404, 410, 500)) {
            assertEquals(AttachmentFailure.MISSING, attachmentFailure(status))
        }
    }

    @Test
    fun a_throwable_without_an_http_status_reads_as_no_status() {
        assertEquals(null, attachmentStatus(RuntimeException("decode failed")))
        assertEquals(null, attachmentStatus(null))
    }
}
