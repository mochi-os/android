// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.wikis.R
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.attachments.AttachmentsEvent
import org.mochios.wikis.ui.attachments.AttachmentsViewModel
import org.mochios.wikis.ui.components.TagManagerSnackbar
import org.mochios.wikis.ui.components.TagManagerViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Removing a tag and editing an attachment's caption, as the web has them:
 * each shows on the tap, before the server answers, and goes back with the
 * error if the server refuses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikiInstantTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: WikisRepository
    private lateinit var session: SessionManager

    /** Holds every write until released, so a test sees the screen before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the writes it is sent. */
    @Volatile private var refuse = false

    /** Each write the server has been sent, by path. */
    private val writes = ConcurrentLinkedQueue<String>()

    private val jobs = mutableListOf<Job>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when (path) {
                    "/wikis/w1/-/info" -> ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki"}, "permissions": {}}""")
                    "/wikis/w1/-/attachment/list" ->
                        ok("""{"attachments": [{"id": "a1", "name": "one.png", "caption": "Old"}, {"id": "a2", "name": "two.png", "caption": ""}]}""")
                    "/wikis/w1/-/pages/home/tag/remove" -> {
                        writes.add(path)
                        answer("{}")
                    }
                    "/wikis/w1/-/attachment/update" -> {
                        writes.add(path)
                        answer("""{"attachment": {"id": "a1", "name": "one.png", "caption": "New"}}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikisApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(server.url("/").toString().trimEnd('/')) }
        repository = WikisRepository(api, FileStore(context), context)
    }

    @After
    fun end() {
        gate.countDown()
        jobs.forEach { it.cancel() }
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun answer(data: String): MockResponse {
        gate.await(5, TimeUnit.SECONDS)
        return if (refuse) MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""") else ok(data)
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun tags(): Pair<TagManagerViewModel, MutableList<TagManagerSnackbar>> {
        val model = TagManagerViewModel(SavedStateHandle(), repository)
        val said = mutableListOf<TagManagerSnackbar>()
        jobs += CoroutineScope(Dispatchers.Main).launch { model.snackbar.collect { said += it } }
        return model to said
    }

    private fun attachments(): Pair<AttachmentsViewModel, MutableList<AttachmentsEvent>> {
        val model = AttachmentsViewModel(SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home")), repository, session)
        val events = mutableListOf<AttachmentsEvent>()
        jobs += CoroutineScope(Dispatchers.Main).launch { model.events.collect { events += it } }
        until { model.uiState.value.attachments.size == 2 }
        return model to events
    }

    private fun AttachmentsViewModel.caption(id: String) = uiState.value.attachments.single { it.id == id }.caption

    @Test
    fun `a removed tag's chip goes on the tap, and back in its place with the error when refused`() {
        val (model, said) = tags()
        model.remove("w1", "home", listOf("alpha", "beta", "gamma"), "beta")
        assertEquals(listOf("alpha", "gamma"), model.uiState.value.lastUpdated)
        until { writes.contains("/wikis/w1/-/pages/home/tag/remove") }

        refuse = true
        gate.countDown()
        until { said.isNotEmpty() }
        assertEquals(R.string.wikis_tag_manager_remove_failed, said.single().messageRes)
        assertEquals(listOf("alpha", "beta", "gamma"), model.uiState.value.lastUpdated)
    }

    @Test
    fun `an accepted removal stays gone and says so`() {
        val (model, said) = tags()
        model.remove("w1", "home", listOf("alpha", "beta"), "beta")
        gate.countDown()
        until { said.isNotEmpty() }
        assertEquals(R.string.wikis_tag_manager_removed, said.single().messageRes)
        assertEquals(listOf("alpha"), model.uiState.value.lastUpdated)
    }

    @Test
    fun `a caption shows as the editor closes, and goes back with the error when refused`() {
        val (model, events) = attachments()
        model.requestCaption(model.uiState.value.attachments.first())
        model.saveCaption("New", "Failed to save caption")
        assertEquals("New", model.caption("a1"))
        until { writes.contains("/wikis/w1/-/attachment/update") }

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertTrue(events.single() is AttachmentsEvent.Toast)
        assertEquals("Old", model.caption("a1"))
        assertEquals("", model.caption("a2"))
    }

    @Test
    fun `an accepted caption keeps the server's answer`() {
        val (model, events) = attachments()
        model.requestCaption(model.uiState.value.attachments.first())
        model.saveCaption("New", "Failed to save caption")
        gate.countDown()
        until { writes.contains("/wikis/w1/-/attachment/update") }
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
        assertEquals("New", model.caption("a1"))
        assertTrue(events.isEmpty())
    }
}
