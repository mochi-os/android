// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mochios.android.push.RegistrationMemo.Verdict

/**
 * The memo is what keeps a resume from re-registering an unchanged phone and
 * from retrying a refused registration on every resume. These pin the three
 * verdicts against the record's age, its key and the order of success and
 * refusal.
 */
class RegistrationMemoTest {

    private val server = "https://example.mochi-os.org"
    private val credential = RegistrationMemo.fingerprint("token", "installation")
    private val hour = 60L * 60 * 1000
    private val now = 1_000_000_000_000L

    private fun registration(at: Long, transport: String = "fcm", credential: String = this.credential) =
        Registration(server, transport, credential, at)

    private fun judge(last: Registration? = null, refusal: Registration? = null, at: Long = now) =
        RegistrationMemo.judge(at, server, "fcm", credential, last, refusal)

    @Test
    fun `nothing recorded registers`() {
        assertEquals(Verdict.REGISTER, judge())
    }

    @Test
    fun `a recent success is fresh and an old one is not`() {
        assertEquals(Verdict.FRESH, judge(last = registration(now - hour)))
        assertEquals(Verdict.REGISTER, judge(last = registration(now - RegistrationMemo.WINDOW)))
    }

    @Test
    fun `a success for another credential, transport or server does not count`() {
        assertEquals(Verdict.REGISTER, judge(last = registration(now - hour, credential = "other")))
        assertEquals(Verdict.REGISTER, judge(last = registration(now - hour, transport = "unifiedpush")))
        val elsewhere = Registration("https://other.example", "fcm", credential, now - hour)
        assertEquals(Verdict.REGISTER, judge(last = elsewhere))
    }

    @Test
    fun `a recent refusal is refused even after an earlier success`() {
        assertEquals(Verdict.REFUSED, judge(refusal = registration(now - hour)))
        assertEquals(
            Verdict.REFUSED,
            judge(last = registration(now - 2 * hour), refusal = registration(now - hour)),
        )
    }

    @Test
    fun `a refusal older than the window is retried`() {
        assertEquals(Verdict.REGISTER, judge(refusal = registration(now - RegistrationMemo.WINDOW)))
    }

    @Test
    fun `a record from the future registers`() {
        assertEquals(Verdict.REGISTER, judge(last = registration(now + hour)))
    }

    @Test
    fun `fingerprints are stable, order-sensitive and unambiguous`() {
        assertEquals(RegistrationMemo.fingerprint("a", "b"), RegistrationMemo.fingerprint("a", "b"))
        assertNotEquals(RegistrationMemo.fingerprint("a", "b"), RegistrationMemo.fingerprint("b", "a"))
        assertNotEquals(RegistrationMemo.fingerprint("ab", "c"), RegistrationMemo.fingerprint("a", "bc"))
        assertEquals(64, RegistrationMemo.fingerprint("token").length)
    }
}
