// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.attachments

import android.content.ClipboardManager
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.Downloads
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * What the attachments page offers each reader: uploading, captioning and
 * deleting to those the server lets do them, copying the embed link and
 * saving a file to everyone; a finished upload says how many files went, and
 * the embed link a picture copies shows its thumbnail, as the editor's does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AttachmentsActionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: WikisRepository

    /** What the wiki's information grants the reader. */
    @Volatile private var permissions = """{"view": true}"""

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?")) {
                    "/wikis/w1/-/info" -> ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki"}, "permissions": $permissions}""")
                    "/wikis/w1/-/attachment/list" -> ok("""{"attachments": []}""")
                    "/wikis/w1/-/attachment/upload" ->
                        ok("""{"attachments": [{"id": "a1", "name": "one.pdf", "type": "application/pdf"}]}""")
                    else -> MockResponse().setResponseCode(404)
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
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        rule.waitForIdle()
    }

    private fun shows(text: String): Boolean = runCatching { rule.onNodeWithText(text).assertExists() }.isSuccess

    private fun screen(): AttachmentsViewModel {
        val model = AttachmentsViewModel(
            SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home")),
            repository,
            session,
            Downloads(context, FileStore(context)),
        )
        rule.setContent { AttachmentsScreen(navController = rememberNavController(), viewModel = model) }
        until { !model.uiState.value.isLoading && shows("No attachments yet") }
        return model
    }

    @Test
    fun `a reader who may not edit is offered no upload`() {
        screen()
        rule.onNodeWithText("Upload files").assertDoesNotExist()
    }

    @Test
    fun `an editor is offered the upload, and a finished one says how many files went`() {
        permissions = """{"view": true, "edit": true}"""
        val model = screen()
        rule.onNodeWithText("Upload files").assertExists()
        val file = File(context.filesDir, "one.pdf").apply { writeText("bytes") }
        model.uploadAttachments(listOf(Uri.fromFile(file)), "Failed to upload files")
        until { shows("1 file uploaded") }
        rule.onNodeWithText("Uploading…").assertDoesNotExist()
    }

    private val wiki = WikiContextValue("w1", WikiInfo(id = "w1"), WikiPermissions(view = true), "https://server")

    private val picture = Attachment(id = "a1", name = "plan [v2].png", type = "image/png")

    private fun menu(granted: WikiPermissions) {
        rule.setContent {
            CompositionLocalProvider(LocalWikiContext provides wiki) {
                AttachmentsBody(
                    state = AttachmentsUiState(
                        isLoading = false,
                        attachments = listOf(picture),
                        wiki = WikiInfo(id = "w1"),
                        permissions = granted,
                        viewMode = AttachmentsViewMode.LIST,
                    ),
                    baseURL = "https://server/wikis/w1/-/", onDownload = {},
                    onSearchChange = {}, onClearSearch = {}, onFilterChange = {}, onSortChange = {}, onViewModeChange = {},
                    onRefresh = {}, onRequestDelete = {}, onRequestCaption = {}, onRetry = {},
                )
            }
        }
        rule.onNodeWithContentDescription("More options").performClick()
        rule.waitForIdle()
    }

    @Test
    fun `a reader's menu copies and saves, and neither captions nor deletes`() {
        menu(WikiPermissions(view = true))
        rule.onNodeWithText("Copy embed link").assertExists()
        rule.onNodeWithText("Download").assertExists()
        rule.onNodeWithText("Add caption").assertDoesNotExist()
        rule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `an editor captions, and does not delete without the delete permission`() {
        menu(WikiPermissions(view = true, edit = true))
        rule.onNodeWithText("Add caption").assertExists()
        rule.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `the delete permission offers Delete`() {
        menu(WikiPermissions(view = true, delete = true))
        rule.onNodeWithText("Delete").assertExists()
        rule.onNodeWithText("Add caption").assertDoesNotExist()
    }

    @Test
    fun `a picture's embed link shows its thumbnail, its name escaped`() {
        menu(WikiPermissions(view = true))
        rule.onNodeWithText("Copy embed link").performClick()
        rule.waitForIdle()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        until { clipboard.primaryClip != null }
        assertEquals(
            "![plan \\[v2\\].png](attachments/a1/thumbnail)",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
    }
}
