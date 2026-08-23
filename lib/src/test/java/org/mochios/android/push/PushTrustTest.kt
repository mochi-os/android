// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mochi's own distributor hands the payload over in cleartext, so the
 * connector's decrypted flag - the only authenticity signal on the
 * third-party path - is false for every one of them and drops the lot. On
 * the local action the sender's identity replaces it: only a package sharing
 * our signature may raise a notification.
 */
class PushTrustTest {

    private val payload = """{"title":"Hi"}""".toByteArray()

    @Test
    fun `cleartext from our own distributor is delivered`() {
        // The bug: this is the entire live self-hosted push path, and the
        // decrypted gate refused all of it.
        assertEquals(LocalPush.ACCEPT, judgeLocalPush("org.mochios.mochi", true, payload))
    }

    @Test
    fun `a broadcast carrying no PendingIntent is refused`() {
        // An outsider can send this action - it is exported - but cannot
        // forge a PendingIntent whose creator is someone else.
        assertEquals(LocalPush.UNIDENTIFIED, judgeLocalPush(null, false, payload))
        // Nor by claiming trust it was not granted: no sender, no delivery.
        assertEquals(LocalPush.UNIDENTIFIED, judgeLocalPush(null, true, payload))
    }

    @Test
    fun `an identified sender that is not signature-matched is refused`() {
        assertEquals(LocalPush.UNTRUSTED, judgeLocalPush("com.example.attacker", false, payload))
    }

    @Test
    fun `a trusted sender with no payload is refused`() {
        assertEquals(LocalPush.EMPTY, judgeLocalPush("org.mochios.mochi", true, null))
        assertEquals(LocalPush.EMPTY, judgeLocalPush("org.mochios.mochi", true, ByteArray(0)))
    }

    @Test
    fun `identity is checked before the payload`() {
        // An untrusted empty broadcast reports the reason that matters, so
        // the log names the refused package rather than an empty body.
        assertEquals(LocalPush.UNTRUSTED, judgeLocalPush("com.example.attacker", false, null))
    }
}
