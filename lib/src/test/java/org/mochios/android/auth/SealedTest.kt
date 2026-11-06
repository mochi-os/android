// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * AndroidKeyStore does not exist off-device, so these drive [Sealed] with a
 * plain AES key. What is under test is the wrapping and the read path either
 * side of an upgrade, not the keystore binding.
 */
class SealedTest {

    private class Fixed(private val key: SecretKey) : Vault {
        override fun key() = key
    }

    private fun key(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun sealed(k: SecretKey = key()) = Sealed(Fixed(k))

    @Test
    fun `a sealed value round-trips`() {
        val subject = sealed()
        val cookie = "abc123.def456"
        assertEquals(cookie, subject.open(subject.seal(cookie)))
    }

    @Test
    fun `the stored form does not contain the plaintext`() {
        val cookie = "abc123.def456"
        val stored = sealed().seal(cookie)
        assertFalse(stored.contains(cookie))
    }

    @Test
    fun `two seals of one value differ, so the nonce is not reused`() {
        val subject = sealed()
        assertFalse(subject.seal("same") == subject.seal("same"))
    }

    @Test
    fun `a value written before wrapping existed still reads`() {
        // The upgrade path: DataStore holds a bare cookie, and the read has to
        // return it rather than sign the user out.
        assertEquals("legacy-cookie", sealed().open("legacy-cookie"))
    }

    @Test
    fun `a value the key can no longer open reads as absent`() {
        // What a device does when it discards the key - a changed screen lock.
        val stored = sealed().seal("abc123")
        assertNull(sealed().open(stored))
    }

    /** A vault that counts how often the key is asked for. */
    private class Counted(private val key: SecretKey) : Vault {
        var asked = 0
        override fun key(): SecretKey {
            asked++
            return key
        }
    }

    @Test
    fun `a value read again is not opened through the key again`() {
        val vault = Counted(key())
        val subject = Sealed(vault)
        val stored = subject.seal("abc123")
        assertEquals("abc123", subject.open(stored))
        val once = vault.asked
        repeat(5) { assertEquals("abc123", subject.open(stored)) }
        assertEquals(once, vault.asked)
    }

    @Test
    fun `a forgotten value is opened through the key again`() {
        val vault = Counted(key())
        val subject = Sealed(vault)
        val stored = subject.seal("abc123")
        subject.open(stored)
        val once = vault.asked
        subject.forget()
        assertEquals("abc123", subject.open(stored))
        assertEquals(once + 1, vault.asked)
    }

    @Test
    fun `a truncated or corrupt sealed value reads as absent`() {
        val subject = sealed()
        assertNull(subject.open("sealed:"))
        assertNull(subject.open("sealed:not-base64!!"))
        assertNull(subject.open(subject.seal("abc123").dropLast(4)))
    }
}
