// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Source of the AES key values are wrapped with. Production uses
 * [Keystore]; a test supplies its own key, since AndroidKeyStore does not
 * exist off-device.
 */
interface Vault {
    fun key(): SecretKey
}

/**
 * The AES key held in AndroidKeyStore under [alias], created on first use.
 * The key never leaves the keystore, so a copy of the app's files is not
 * enough to read what it wrapped.
 */
class Keystore(private val alias: String) : Vault {
    override fun key(): SecretKey {
        held[alias]?.let { return it }
        return synchronized(held) { held[alias] ?: find().also { held[alias] = it } }
    }

    private fun find(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = store.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not setUserAuthenticationRequired: push delivery
                // and the boot-time session read both run with the screen
                // locked, and a key they cannot reach signs the user out.
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"

        // Each key's handle, once found or made: each lookup was a keystore load
        // and an entry read across the keystore's binder, for every value read
        // or written. It names the key; the key itself stays in the keystore.
        // Held for the process, not the instance: the session and every push
        // store build their own over one alias, and two that both found no key
        // would each make one, the second replacing the first under the alias
        // while the first went on using the key it replaced.
        val held = ConcurrentHashMap<String, SecretKey>()
    }
}

/**
 * AES-GCM wrapping for the credential values the app stores: the session
 * cookie, every per-app JWT, the OAuth PKCE verifiers and the push
 * registrations. The storage layout is unchanged - only the values are
 * wrapped - so this is a drop-in around an existing read or write.
 *
 * A value written before this existed carries no marker, and [open] returns it
 * as it stands; the next write seals it. That makes the migration silent and
 * lossless in both directions during an upgrade.
 */
class Sealed(private val vault: Vault) {

    /**
     * Values already opened, by their stored form: the session and each app's
     * token were read through the keystore on every request. Every seal takes a
     * fresh nonce, so a changed value is a new stored form and an entry here
     * never goes stale; [forget] drops the plaintexts when the credentials go.
     */
    private val opened = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > OPENED
    }

    /** Drop every value held in the clear. */
    fun forget() {
        synchronized(opened) { opened.clear() }
    }

    /** [plain] as `<marker><base64 of nonce ‖ ciphertext>`. */
    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, vault.key())
        val nonce = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return MARKER + Base64.getEncoder().encodeToString(nonce + sealed)
    }

    /**
     * The plaintext behind [stored]: the value itself when it was written
     * before wrapping existed, and null when it cannot be unwrapped. Null is
     * the honest answer for a key the device has invalidated (a changed screen
     * lock discards it), and reads as "no credential" - the user signs in
     * again, which is what actually has to happen.
     */
    fun open(stored: String): String? {
        if (!stored.startsWith(MARKER)) return stored
        synchronized(opened) { opened[stored] }?.let { return it }
        return try {
            val raw = Base64.getDecoder().decode(stored.removePrefix(MARKER))
            if (raw.size <= NONCE) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                vault.key(),
                GCMParameterSpec(TAG, raw, 0, NONCE),
            )
            String(cipher.doFinal(raw, NONCE, raw.size - NONCE), Charsets.UTF_8)
                .also { plain -> synchronized(opened) { opened[stored] = plain } }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        // Not valid base64, so it can never be the leading bytes of a value
        // written before wrapping existed.
        const val MARKER = "sealed:"
        const val NONCE = 12
        const val TAG = 128
        // The session, one token per app and the verifiers, with room to spare.
        const val OPENED = 64
    }
}
