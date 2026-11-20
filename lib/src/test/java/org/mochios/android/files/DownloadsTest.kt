// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.api.unwrapRaw
import org.mochios.android.i18n.AppContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * A file the server holds, saved into a document the user chose through the
 * app's own client: written whole, said so when it ends, and a failed one
 * leaves no half-written document behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var api: Files
    private lateinit var downloads: Downloads

    private interface Files {
        @Streaming
        @GET("{name}")
        suspend fun file(@Path("name") name: String): Response<ResponseBody>
    }

    /** A documents provider whose documents are files, which records each deletion. */
    class Documents : ContentProvider() {
        override fun onCreate() = true

        private fun file(uri: Uri) = File(context!!.cacheDir, DocumentsContract.getDocumentId(uri))

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(
            file(uri),
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        )

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            if (method.endsWith("deleteDocument")) {
                // DocumentsContract names the document under "uri".
                val uri = extras?.getParcelable("uri", Uri::class.java)
                uri?.let { document -> file(document).delete() }
            }
            return Bundle()
        }

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private fun document(name: String): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, name)

    private fun written(name: String) = File(context.cacheDir, name)

    @Before
    fun begin() {
        AppContext.set(context)
        Robolectric.setupContentProvider(Documents::class.java, AUTHORITY)
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/")).build().create(Files::class.java)
        downloads = Downloads(context, FileStore(context))
        ShadowToast.reset()
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun finish(job: Job) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!job.isCompleted) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "the transfer did not end" }
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun bytes(size: Int) = Random(7).nextBytes(size)

    @Test
    fun `a file is streamed whole into the document the user chose`() {
        val served = bytes(300_000)
        server.enqueue(MockResponse().setBody(Buffer().write(served)))
        val job = downloads.save(document("archive.zip"), "archive.zip") { api.file("archive.zip").unwrapRaw() }
        finish(job)
        assertArrayEquals(served, written("archive.zip").readBytes())
        assertEquals("/archive.zip", server.takeRequest().path)
        assertEquals(context.getString(R.string.downloads_saved, "archive.zip"), ShadowToast.getTextOfLatestToast())
        assertTrue(downloads.running.value.isEmpty())
    }

    @Test
    fun `a refusal says why and leaves no document behind`() {
        written("gone.zip").writeText("stale")
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error": "Backup not found"}"""))
        finish(downloads.save(document("gone.zip"), "gone.zip") { api.file("gone.zip").unwrapRaw() })
        assertEquals(context.getString(R.string.downloads_failed, "gone.zip", "Backup not found"), ShadowToast.getTextOfLatestToast())
        assertFalse(written("gone.zip").exists())
    }

    @Test
    fun `a connection broken part way says so and leaves no half document`() {
        server.enqueue(
            MockResponse().setBody(Buffer().write(bytes(2_000_000)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        finish(downloads.save(document("half.zip"), "half.zip") { api.file("half.zip").unwrapRaw() })
        val network = context.getString(R.string.error_network)
        assertEquals(context.getString(R.string.downloads_failed, "half.zip", network), ShadowToast.getTextOfLatestToast())
        assertFalse(written("half.zip").exists())
    }

    @Test
    fun `the transfer outlives the screen that started it`() {
        val served = bytes(200_000)
        server.enqueue(MockResponse().setBody(Buffer().write(served)).throttleBody(50_000, 100, TimeUnit.MILLISECONDS))
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var job: Job? = null
        screen.launch {
            job = downloads.save(document("long.zip"), "long.zip") { api.file("long.zip").unwrapRaw() }
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (job == null) {
            check(System.currentTimeMillis() < deadline) { "the transfer did not start" }
            Thread.sleep(5)
        }
        assertTrue(downloads.running.value.contains(document("long.zip")))
        // The user leaves: the screen's scope ends with the transfer under way.
        screen.cancel()
        finish(job!!)
        assertArrayEquals(served, written("long.zip").readBytes())
        assertEquals(context.getString(R.string.downloads_saved, "long.zip"), ShadowToast.getTextOfLatestToast())
    }

    private companion object {
        const val AUTHORITY = "org.mochios.test.documents"
    }
}
