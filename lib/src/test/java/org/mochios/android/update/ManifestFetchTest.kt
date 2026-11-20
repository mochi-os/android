// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.update

import android.content.Context
import androidx.work.NetworkType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * The manifest is trusted only signed, current and well formed, on every
 * Android version; anything else is a check that failed, never "up to date",
 * which is what someone holding clients on an old release would want them to
 * read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestFetchTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val key: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val published = Base64.getEncoder().encodeToString(key.public.encoded.takeLast(32).toByteArray())
    private val now = 1_800_000_000L

    // What the package host answers.
    @Volatile private var body = ""
    @Volatile private var signature: String? = null
    @Volatile private var status = 200

    @Before
    fun begin() {
        context.getSharedPreferences(UpdateChecker.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/versions.json" -> MockResponse().setResponseCode(status).setBody(body)
                "/versions.json.sig" -> signature?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun sign(text: String): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(key.private)
        signer.update(text.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    /** Serve a signed manifest. */
    private fun serve(version: String = "1.17", generated: Long? = now - 60, platform: String = "android") {
        val time = generated?.let { "\"generated\": $it, " }.orEmpty()
        body = """{$time"platform": "$platform", "tracks": {"production": "$version"}, """ +
            """"releases": {"$version": {"file": "mochi-$version.apk", "size": 10, "sha256": "ab"}}}"""
        signature = sign(body)
    }

    private fun fetch(platform: Boolean = true) =
        UpdateChecker.fetchManifest(context, server.url("/").toString().trimEnd('/'), published, platform, now)

    private fun refused(platform: Boolean = true) {
        try {
            fetch(platform)
            fail("the manifest was trusted")
        } catch (_: ManifestRefused) {
        }
    }

    @Test
    fun `a signed current manifest is trusted on every version`() {
        serve()
        for (platform in listOf(true, false)) {
            val manifest = fetch(platform)!!
            assertEquals("1.17", manifest.version)
            assertEquals(Release("mochi-1.17.apk", 10, "ab"), manifest.release)
        }
    }

    @Test
    fun `an error status is refused, not up to date`() {
        serve()
        status = 503
        refused()
    }

    @Test
    fun `a manifest with no signature or a bad one is refused on every version`() {
        serve()
        signature = null
        refused(true)
        refused(false)
        signature = sign(body.replace("1.17", "1.18"))
        refused(true)
        refused(false)
    }

    /** The version becomes a file name: `99/a` would make a directory, and `..` climb out of it. */
    @Test
    fun `a version not of the build's shape is refused though signed`() {
        for (version in listOf("99/a", "99/../../x", "1.17-beta", "1", "1.2.3.4", "")) {
            serve(version = version)
            if (version.isEmpty()) {
                assertNull("no production version is no update", fetch())
            } else {
                refused()
            }
        }
        for (version in listOf("1.17", "2.0.1")) {
            serve(version = version)
            assertEquals(version, fetch()!!.version)
        }
    }

    /** Core's replay rule: an old signed manifest cannot hold the phone on a release it has passed. */
    @Test
    fun `a manifest older than the last accepted is refused`() {
        serve(generated = now - 60)
        fetch()
        serve(generated = now - 120)
        refused()
        serve(generated = now - 60)
        assertEquals("the same one again is fine", "1.17", fetch()!!.version)
    }

    @Test
    fun `a manifest from the future, of no time or another platform is refused`() {
        serve(generated = now + 2 * 24 * 60 * 60)
        refused()
        serve(generated = null)
        refused()
        serve(platform = "linux")
        refused()
        serve(generated = now + 60 * 60)
        assertEquals("modest skew is allowed", "1.17", fetch()!!.version)
    }

    @Test
    fun `a refused manifest does not raise the watermark`() {
        serve(version = "99/a", generated = now)
        refused()
        serve(generated = now - 60)
        assertEquals("1.17", fetch()!!.version)
    }

    /** The daily poll is small and runs on any network; the APK it finds waits for an unmetered one. */
    @Test
    fun `the poll needs a network and the download an unmetered one`() {
        val poll = UpdateChecker.pollRequest().workSpec
        assertEquals(NetworkType.CONNECTED, poll.constraints.requiredNetworkType)
        assertTrue(poll.input.getBoolean("poll", false))
        assertEquals(24L * 60 * 60 * 1000, poll.intervalDuration)
        val download = UpdateChecker.unmeteredRequest().workSpec
        assertEquals(NetworkType.UNMETERED, download.constraints.requiredNetworkType)
    }
}
