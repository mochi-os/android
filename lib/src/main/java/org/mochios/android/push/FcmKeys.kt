// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.content.SharedPreferences
import com.google.crypto.tink.apps.fixed_webpush.WebPushHybridDecrypt
import org.json.JSONObject
import org.mochios.android.auth.Keystore
import org.mochios.android.auth.Sealed
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * This device's Web Push keys for the FCM transport: a P-256 key pair and a
 * 16-byte authentication secret. The server seals every FCM message to them
 * (RFC 8291, one aes128gcm record), so Google relays ciphertext, and [open]
 * reads it.
 *
 * Kept in the app's own preferences, sealed with the AndroidKeyStore key the
 * session uses. The UnifiedPush connector's key store cannot hold them: its
 * keys table references its registrations, and the FCM transport is not one.
 */
class FcmKeys internal constructor(
    private val prefs: SharedPreferences,
    private val sealed: Sealed,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        Sealed(Keystore(KEYSTORE_ALIAS)),
    )

    /** What the server is given: the secret and the public key's uncompressed point, base64url. */
    data class Public(val auth: String, val p256dh: String)

    private class Held(val secret: ByteArray, val point: ByteArray, val scalar: ByteArray)

    /**
     * The keys the server seals to, made and stored on first use. A stored
     * pair that no longer opens (the keystore key was invalidated) is
     * replaced, and the registration that follows gives the server the new
     * one. Throws when the keys cannot be stored.
     */
    fun public(): Public = synchronized(LOCK) {
        val held = read() ?: create()
        Public(encode(held.secret), encode(held.point))
    }

    /**
     * [message] opened with the stored keys, or null when the device holds
     * none. Throws when the message will not open with them.
     */
    fun open(message: ByteArray): ByteArray? {
        val held = read() ?: return null
        return webPushOpen(message, held.secret, held.point, held.scalar)
    }

    /** Forget the keys, so the next registration is given new ones. */
    fun delete() {
        synchronized(LOCK) { prefs.edit().remove(KEY).commit() }
    }

    private fun read(): Held? {
        val stored = prefs.getString(KEY, null) ?: return null
        val plain = sealed.open(stored) ?: return null
        return try {
            val json = JSONObject(plain)
            Held(
                decode(json.getString("auth")),
                decode(json.getString("public")),
                decode(json.getString("private")),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun create(): Held {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val public = pair.public as ECPublicKey
        val private = pair.private as ECPrivateKey
        val secret = ByteArray(SECRET).also { SecureRandom().nextBytes(it) }
        val held = Held(
            secret,
            byteArrayOf(UNCOMPRESSED) + fixed(public.w.affineX) + fixed(public.w.affineY),
            fixed(private.s),
        )
        val json = JSONObject()
            .put("auth", encode(held.secret))
            .put("public", encode(held.point))
            .put("private", encode(held.scalar))
        // Synchronous: a message can arrive the moment the server holds them.
        check(prefs.edit().putString(KEY, sealed.seal(json.toString())).commit()) { "FCM keys were not stored" }
        return held
    }

    private companion object {
        const val PREFS = "mochi_fcm"
        const val KEY = "keys"

        // The AndroidKeyStore key the session and the push stores seal with.
        const val KEYSTORE_ALIAS = "mochi.credentials"
        const val SECRET = 16
        const val COORDINATE = 32
        const val UNCOMPRESSED: Byte = 4

        // One process-wide lock: the registration and the messaging service
        // each build their own instance over the one stored pair.
        val LOCK = Any()

        fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

        /** [value] as exactly [COORDINATE] big-endian bytes. */
        fun fixed(value: BigInteger): ByteArray {
            val bytes = value.toByteArray()
            val start = maxOf(0, bytes.size - COORDINATE)
            val length = bytes.size - start
            return ByteArray(COORDINATE).also { System.arraycopy(bytes, start, it, COORDINATE - length, length) }
        }
    }
}

/**
 * [message], an RFC 8291 aes128gcm record, opened with a receiver's
 * authentication secret, uncompressed public point and private scalar.
 */
internal fun webPushOpen(message: ByteArray, secret: ByteArray, point: ByteArray, scalar: ByteArray): ByteArray =
    WebPushHybridDecrypt.Builder()
        .withAuthSecret(secret)
        .withRecipientPublicKey(point)
        .withRecipientPrivateKey(scalar)
        .build()
        .decrypt(message, null)
