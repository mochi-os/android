// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.calendars.ui.components.PollReason
import org.mochios.calendars.ui.components.pollReason

/**
 * The failure token a subscription carries, as the drawer reads it. The
 * tokens are the ones `subscription_fetch` in `apps/calendars/calendars.star`
 * records: empty, `too_large`, `invalid`, or `status:<n>`.
 */
class PollReasonTest {

    @Test
    fun `a calendar that fetched cleanly has nothing to say`() {
        assertNull(pollReason(""))
        assertNull(pollReason("   "))
    }

    @Test
    fun `the tokens the server records read as themselves`() {
        assertEquals(PollReason.Large, pollReason("too_large"))
        assertEquals(PollReason.Invalid, pollReason("invalid"))
    }

    @Test
    fun `a status carries its code`() {
        assertEquals(PollReason.Status(404), pollReason("status:404"))
        assertEquals(PollReason.Status(502), pollReason("status:502"))
    }

    /** The server writes a status of 0 when nothing answered at all. */
    @Test
    fun `a status of zero is nothing answering, not an answer of zero`() {
        assertEquals(PollReason.Unreachable, pollReason("status:0"))
    }

    /**
     * A server one release ahead can record a reason this one has no words
     * for. Showing the token beats showing an empty line.
     */
    @Test
    fun `a token this release does not know is shown as it came`() {
        assertEquals(PollReason.Other("throttled"), pollReason("throttled"))
        assertEquals(PollReason.Other("status:later"), pollReason("status:later"))
    }

    @Test
    fun `surrounding space is not a reason of its own`() {
        assertEquals(PollReason.Status(404), pollReason("  status:404  "))
        assertEquals(PollReason.Large, pollReason(" too_large "))
    }
}
