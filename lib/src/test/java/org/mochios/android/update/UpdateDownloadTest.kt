// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A download WorkManager stops - the Wi-Fi an unmetered download waits for is
 * lost, or the job's time runs out - must not leave the process believing it
 * is still running: the About dialog froze on the last percentage with its
 * button disabled, and every rerun of the worker backed off from the stale
 * state, until the process died.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateDownloadTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val key: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val published = Base64.getEncoder().encodeToString(key.public.encoded.takeLast(32).toByteArray())
    private val apk = ByteArray(200_000) { (it % 251).toByte() }

    // The APK request has reached the server, and the server may answer it.
    private val arrived = CountDownLatch(1)
    private val released = CountDownLatch(1)

    // Hold the APK request, then drop it, as a lost network does.
    @Volatile private var hold = true

    @Before
    fun begin() {
        context.getSharedPreferences(UpdateChecker.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        UpdateChecker.updatesDir(context).deleteRecursively()
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).versionName = "1.17"
        val sum = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        val manifest = """{"generated": ${System.currentTimeMillis() / 1000 - 60}, "platform": "android", """ +
            """"tracks": {"production": "1.18"}, """ +
            """"releases": {"1.18": {"file": "mochi-1.18.apk", "size": ${apk.size}, "sha256": "$sum"}}}"""
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/versions.json" -> MockResponse().setBody(manifest)
                "/versions.json.sig" -> MockResponse().setBody(sign(manifest))
                "/mochi-1.18.apk" -> if (hold) {
                    arrived.countDown()
                    released.await(30, TimeUnit.SECONDS)
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                } else {
                    MockResponse().setBody(Buffer().write(apk))
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun end() {
        released.countDown()
        server.shutdown()
    }

    private fun sign(text: String): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(key.private)
        signer.update(text.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private suspend fun check(): CheckOutcome =
        UpdateChecker.checkNow(context, server.url("/").toString().trimEnd('/'), published, true)

    @Test
    fun `a stopped download stops running, and the next check stages it`() = runBlocking {
        val job = launch(Dispatchers.IO) { check() }
        assertTrue(arrived.await(10, TimeUnit.SECONDS))
        assertTrue(UpdateChecker.state.value is DownloadState.Running)

        job.cancel()
        released.countDown()
        withTimeout(10_000) { job.join() }
        assertFalse(UpdateChecker.state.value is DownloadState.Running)

        hold = false
        assertEquals(CheckOutcome.UpdateStaged, check())
        assertEquals(DownloadState.Staged("1.18"), UpdateChecker.state.value)
    }

    @Test
    fun `a stopped download ends at once, not at the read timeout`() = runBlocking {
        val job = launch(Dispatchers.IO) { check() }
        assertTrue(arrived.await(10, TimeUnit.SECONDS))

        // The server never answers: only cancelling the call ends the read.
        job.cancel()
        withTimeout(10_000) { job.join() }
        assertFalse(UpdateChecker.state.value is DownloadState.Running)
    }
}
