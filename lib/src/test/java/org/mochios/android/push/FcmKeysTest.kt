// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import com.google.crypto.tink.apps.fixed_webpush.WebPushHybridEncrypt
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The FCM transport's Web Push keys, through the store the app keeps them in:
 * made on first use, posted with the registration, and opening what the
 * server seals to them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FcmKeysTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    @Before
    fun begin() {
        FakeKeystore.install()
        FcmKeys(context).delete()
        RegistrationStore(context).clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    /** What core does: the fields as JSON, sealed to the device's keys in one aes128gcm record, base64url. */
    private fun sealed(fields: Map<String, String>, auth: String, p256dh: String): String {
        val plain = JSONObject(fields).toString().toByteArray(Charsets.UTF_8)
        val message = WebPushHybridEncrypt.Builder()
            .withAuthSecret(decode(auth))
            .withRecipientPublicKey(decode(p256dh))
            .build()
            .encrypt(plain, null)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(message)
    }

    /** A registration against the mock server; the request it posted. */
    private fun registered(): JSONObject {
        server.enqueue(MockResponse().setBody("""{"data":{"id":"account1"}}"""))
        val kept = mutableListOf<String>()
        val outcome = runBlocking {
            FcmRegistrar.registerWith(
                context,
                OkHttpClient(),
                server.url("/").toString(),
                "installation1",
                token = { "token1" },
                device = { "device-0001" },
                accepted = { account -> kept += account },
            )
        }
        assertEquals(FcmRegistrar.Outcome.REGISTERED, outcome)
        assertEquals(listOf("account1"), kept)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/notifications/-/push/register/fcm", request.path)
        assertEquals("Bearer token1", request.getHeader("Authorization"))
        return JSONObject(request.body.readUtf8())
    }

    @Test
    fun `a device whose keys cannot be stored does not register for FCM`() {
        val outcome = runBlocking {
            FcmRegistrar.registerWith(
                context,
                OkHttpClient(),
                server.url("/").toString(),
                "installation1",
                token = { "token1" },
                device = { "device-0001" },
                accepted = { },
                keys = { null },
            )
        }
        // FAILED sends the caller to UnifiedPush, which unregisters this
        // installation: no registration the server could send plain text to.
        assertEquals(FcmRegistrar.Outcome.FAILED, outcome)
        assertEquals("nothing posted", 0, server.requestCount)
        assertNull(RegistrationStore(context).last())
    }

    @Test
    fun `the keys are made once, kept sealed, and the same for every reader`() {
        val first = FcmKeys(context).public()
        assertEquals("secret", 16, decode(first.auth).size)
        val point = decode(first.p256dh)
        assertEquals("uncompressed P-256 point", 65, point.size)
        assertEquals(4, point[0].toInt())
        assertEquals("another reader holds the same keys", first, FcmKeys(context).public())
        val stored = context.getSharedPreferences("mochi_fcm", Context.MODE_PRIVATE).getString("keys", null)!!
        assertTrue("sealed at rest", stored.startsWith("sealed:"))
        assertFalse(stored.contains(first.auth))
    }

    @Test
    fun `the registration carries the stored keys`() {
        val body = registered()
        val keys = FcmKeys(context).public()
        assertEquals("installation1", body.getString("installation"))
        assertEquals("THE POINT: the secret is posted", keys.auth, body.optString("auth"))
        assertEquals("and the public key", keys.p256dh, body.optString("p256dh"))
    }

    @Test
    fun `a message sealed to the registered keys opens`() {
        val body = registered()
        val fields = mapOf("title" to "Ann", "body" to "Meet at noon", "link" to "/chat/c1", "tag" to "t1", "app" to "chat", "id" to "n1")
        val message = sealed(fields, body.getString("auth"), body.getString("p256dh"))
        assertEquals(fields, fcmFields(mapOf("encrypted" to message), FcmKeys(context)::open))
    }

    @Test
    fun `deleted keys are replaced, and what was sealed to them no longer opens`() {
        val old = FcmKeys(context).public()
        val message = sealed(mapOf("body" to "Hello"), old.auth, old.p256dh)
        FcmKeys(context).delete()
        assertNull("no keys held", fcmFields(mapOf("encrypted" to message), FcmKeys(context)::open))
        assertNotEquals(old, FcmKeys(context).public())
        assertNull("new keys do not open it", fcmFields(mapOf("encrypted" to message), FcmKeys(context)::open))
    }
}
