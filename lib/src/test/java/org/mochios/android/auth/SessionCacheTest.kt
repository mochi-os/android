// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.FakeKeystore
import org.mochios.android.ui.components.LastViewedStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the session costs per request and what it leaves behind: the server
 * re-sends the session cookie on every request to extend it, and the image
 * cache serves what it holds without asking the server who is looking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionCacheTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var client: OkHttpClient
    private lateinit var images: ImageLoader

    @Before
    fun begin() {
        FakeKeystore.install()
        server = MockWebServer()
        server.start()
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(server.url("/").toString())
            session.saveSession("first")
        }
        client = OkHttpClient.Builder().cookieJar(session.cookieJar).build()
        images = ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(1 shl 20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "images-test").toOkioPath())
                    .maxSizeBytes(1L shl 20)
                    .build()
            }
            .build()
        SingletonImageLoader.setUnsafe(images)
    }

    @After
    fun end() {
        server.shutdown()
        SingletonImageLoader.reset()
    }

    private fun renewed(value: String) {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "session=$value; Path=/; Max-Age=31536000"))
        client.newCall(Request.Builder().url(server.url("/feeds/-/info")).build()).execute().close()
    }

    @Test
    fun `the server's renewal of the same session writes nothing`() {
        // Every write to the credential store is a new emission of it: a
        // re-sealed session differs from the stored one even when its value
        // does not.
        val writes = AtomicInteger()
        val watch = CoroutineScope(Dispatchers.IO).launch { session.isAuthenticated.collect { writes.incrementAndGet() } }
        try {
            val deadline = System.currentTimeMillis() + 5_000
            while (writes.get() == 0) {
                check(System.currentTimeMillis() < deadline) { "the store never emitted" }
                Thread.sleep(10)
            }
            val before = writes.get()
            repeat(3) { renewed("first") }
            Thread.sleep(300)
            assertEquals(before, writes.get())
        } finally {
            watch.cancel()
        }
        assertEquals("first", runBlocking { session.currentToken.first() })
    }

    @Test
    fun `a session the server changes is kept`() {
        renewed("second")
        assertEquals("second", runBlocking { session.currentToken.first() })
    }

    @Test
    fun `the key is found in the keystore once and then held`() {
        val vault = Keystore("mochi.test.held")
        val first = vault.key()
        // Gone from the keystore, a key looked up afresh would be a new one.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("mochi.test.held")
        assertSame(first, vault.key())
    }

    // The session and each push store build their own over one alias. Two that
    // each made a key would leave one of them sealing with a replaced key.
    @Test
    fun `a second keystore over the same alias uses the key the first holds`() {
        val first = Keystore("mochi.test.shared").key()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("mochi.test.shared")
        assertSame(first, Keystore("mochi.test.shared").key())
    }

    private fun hold() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        images.memoryCache!![MemoryCache.Key("avatar")] = MemoryCache.Value(bitmap.asImage())
        val editor = images.diskCache!!.openEditor("avatar")!!
        images.diskCache!!.fileSystem.write(editor.data) { writeUtf8("bytes") }
        editor.commit()
    }

    private fun held(): Boolean {
        val memory = images.memoryCache!![MemoryCache.Key("avatar")] != null
        val disk = images.diskCache!!.openSnapshot("avatar")?.also { it.close() } != null
        assertEquals("memory and disk agree", memory, disk)
        return memory
    }

    @Test
    fun `signing out empties the image cache`() {
        hold()
        assertNotNull(images.memoryCache!![MemoryCache.Key("avatar")])
        runBlocking { session.clearAll() }
        assertEquals(false, held())
    }

    @Test
    fun `another account taking the binding empties the image cache`() {
        runBlocking { session.setBoundAccount("first-identity") }
        hold()
        runBlocking { session.setBoundAccount("first-identity") }
        assertEquals("the same account keeps its images", true, held())
        runBlocking { session.setBoundAccount("second-identity") }
        assertEquals(false, held())
    }

    @Test
    fun `another server empties the image cache`() {
        hold()
        runBlocking { session.setServerUrl("https://elsewhere.example") }
        assertEquals(false, held())
        assertNull(runBlocking { session.currentToken.first() })
    }

    // Each app reopens the item it last had open, which another account, or
    // the same name on another server, cannot find.
    @Test
    fun `signing out forgets the items the apps last had open`() {
        LastViewedStore.set(context, "feeds", "feed1")
        runBlocking { session.clearAll() }
        assertNull(LastViewedStore.get(context, "feeds"))
    }

    @Test
    fun `another account taking the binding forgets the items the apps last had open`() {
        runBlocking { session.setBoundAccount("first-identity") }
        LastViewedStore.set(context, "forums", "forum1")
        runBlocking { session.setBoundAccount("first-identity") }
        assertEquals("the same account keeps them", "forum1", LastViewedStore.get(context, "forums"))
        runBlocking { session.setBoundAccount("second-identity") }
        assertNull(LastViewedStore.get(context, "forums"))
    }

    // The address is where the app points, not part of the session: emptied,
    // the next sign-in went to mochi-os.org with nothing saying so.
    @Test
    fun `signing out keeps the server address and its theme`() {
        runBlocking {
            session.saveTheme(1f, 2f, 3f)
            session.saveToken("feeds", "token")
            session.setBoundAccount("first-identity")
            session.clearAll()
        }
        val origin = server.url("/").toString().trimEnd('/')
        assertEquals(origin, runBlocking { session.serverUrl.first() })
        assertEquals(origin, session.server.value)
        assertEquals(SessionManager.ThemeAnchors(1f, 2f, 3f), runBlocking { session.themeAnchors.first() })
        assertNull(runBlocking { session.currentToken.first() })
        assertNull(runBlocking { session.getToken("feeds") })
        assertNull(runBlocking { session.getBoundIdentity() })
    }

    @Test
    fun `signing out deletes the downloaded attachments`() {
        val held = File(File(context.cacheDir, "attachments"), "1_report.pdf").apply {
            parentFile!!.mkdirs()
            writeText("x")
        }
        runBlocking { session.clearAll() }
        assertEquals(false, held.exists())
    }

    // A pasted page's address kept its path in every address built from it:
    // the socket, the images and push all went to /login/...
    @Test
    fun `the server is stored as its origin`() {
        runBlocking { session.setServerUrl("https://example.org:8443/login?next=1#top") }
        assertEquals("https://example.org:8443", runBlocking { session.serverUrl.first() })
        assertEquals("held in memory at once", "https://example.org:8443", session.server.value)
        runBlocking { session.setServerUrl("https://example.org:443/") }
        assertEquals("https://example.org", session.server.value)
    }

    // Requests are retargeted from the address held in memory: a write that
    // left it to the store's next emission would send the first requests
    // after it to the server before.
    @Test
    fun `the address is held in memory as it is written`() {
        val still = SessionManager(context, CoroutineScope(java.util.concurrent.Executor { }.asCoroutineDispatcher()))
        runBlocking { still.setServerUrl("https://written.example/login") }
        assertEquals("https://written.example", still.server.value)
    }

    @Test
    fun `an address carrying a user name is refused`() {
        val refused = runCatching { runBlocking { session.setServerUrl("https://user:secret@example.org") } }
        assertEquals(IllegalArgumentException::class.java, refused.exceptionOrNull()?.javaClass)
        assertEquals(server.url("/").toString().trimEnd('/'), session.server.value)
    }

    @Test
    fun `another server forgets the items the apps last had open`() {
        LastViewedStore.set(context, "wikis", "wiki1")
        runBlocking { session.setServerUrl(server.url("/").toString()) }
        assertEquals("the same server keeps them", "wiki1", LastViewedStore.get(context, "wikis"))
        runBlocking { session.setServerUrl("https://elsewhere.example") }
        assertNull(LastViewedStore.get(context, "wikis"))
    }
}
