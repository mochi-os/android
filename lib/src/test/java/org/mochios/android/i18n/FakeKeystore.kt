// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * An "AndroidKeyStore" for Robolectric, which has none: SessionManager seals
 * what it stores with a key kept there, so a test that stores a token or a
 * PKCE verifier needs one. Keys live in memory for the test run.
 */
object FakeKeystore {
    internal val keys = ConcurrentHashMap<String, SecretKey>()

    fun install() {
        if (Security.getProvider("AndroidKeyStore") == null) Security.addProvider(Keys())
    }

    // Android's Provider has only the version-as-a-number constructor.
    @Suppress("DEPRECATION")
    class Keys : Provider("AndroidKeyStore", 1.0, "Robolectric stand-in") {
        init {
            put("KeyStore.AndroidKeyStore", Store::class.java.name)
            put("KeyGenerator.AES", Generator::class.java.name)
        }
    }

    class Store : KeyStoreSpi() {
        override fun engineGetEntry(alias: String, protection: KeyStore.ProtectionParameter?): KeyStore.Entry? =
            keys[alias]?.let { KeyStore.SecretKeyEntry(it) }

        override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]
        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String): Certificate? = null
        override fun engineGetCreationDate(alias: String): Date? = null
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) {
            keys[alias] = key as SecretKey
        }
        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) = Unit
        override fun engineSetCertificateEntry(alias: String, cert: Certificate) = Unit
        override fun engineDeleteEntry(alias: String) {
            keys.remove(alias)
        }
        override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys)
        override fun engineContainsAlias(alias: String) = keys.containsKey(alias)
        override fun engineSize() = keys.size
        override fun engineIsKeyEntry(alias: String) = keys.containsKey(alias)
        override fun engineIsCertificateEntry(alias: String) = false
        override fun engineGetCertificateAlias(cert: Certificate): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    }

    class Generator : KeyGeneratorSpi() {
        private var alias = ""

        override fun engineInit(random: SecureRandom?) = Unit
        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
            alias = (params as KeyGenParameterSpec).keystoreAlias
        }
        override fun engineInit(size: Int, random: SecureRandom?) = Unit
        override fun engineGenerateKey(): SecretKey {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return SecretKeySpec(bytes, "AES").also { keys[alias] = it }
        }
    }
}
