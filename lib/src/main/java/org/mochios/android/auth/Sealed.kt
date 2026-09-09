// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
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
    }
}
