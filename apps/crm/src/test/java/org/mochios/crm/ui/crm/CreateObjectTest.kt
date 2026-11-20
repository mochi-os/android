// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crm

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.crm.CrmServer
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Create sends the object and its values in one request, so a refused value
 * makes nothing; attachments that fail are said, and Create again sends
 * just those to the object already made rather than making another.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class CreateObjectTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: CrmServer

    /** Whether the server refuses an upload of bad.txt, or a copy. */
    @Volatile private var refusing = true

    private val create = "p1/-/objects/create"
    private val upload = "p1/-/objects/o9/attachments/create"
    private val copy = "p1/-/objects/o9/attachments/copy"

    @Before
    fun begin() {
        server = CrmServer(context)
        server.data("p1/-/information", """{"crm": {"id": "p1"},
            "classes": [{"id": "card", "name": "Card", "title": "title"}],
            "fields": {"card": [{"id": "title", "name": "Title", "fieldtype": "text", "rank": 1},
                {"id": "status", "name": "Status", "fieldtype": "text", "rank": 2}]},
            "options": {}, "views": [], "hierarchy": {"card": [""]}}""")
        server.data("p1/-/objects", """{"objects": []}""")
        server.data("p1/-/people", """{"people": []}""")
        server.data(create, """{"id": "o9", "class": "card", "values": {"status": "Done"}}""")
        server.on(upload) { request ->
            if (refusing && request.body.clone().readUtf8().contains("bad.txt")) {
                MockResponse().setResponseCode(400).setBody("""{"error": "File refused"}""")
            } else {
                CrmServer.ok("""{"attachments": []}""")
            }
        }
        server.on(copy) {
            if (refusing) MockResponse().setResponseCode(500).setBody("""{"error": "Copy failed"}""")
            else CrmServer.ok("""{"attachments": []}""")
        }
    }

    @After
    fun end() = server.stop()

    private fun model(): CreateObjectViewModel {
        val model = CreateObjectViewModel(SavedStateHandle(mapOf("crmId" to "p1")), server.repository(), ActiveViewStore(context))
        until("the form") { !model.uiState.value.isLoading }
        return model
    }

    private fun file(name: String): Uri {
        val folder = File(context.filesDir, "picked").apply { mkdirs() }
        val file = File(folder, name).apply { writeText("contents of $name") }
        return Uri.fromFile(file)
    }

    private fun finished(model: CreateObjectViewModel) =
        until("the create") { !model.uiState.value.isCreating && server.of(create).isNotEmpty() }

    @Test
    fun `the values go with the create, in the one request`() {
        val model = model()
        model.createObject("card", "Hello", initialValues = mapOf("status" to "Done"))
        until("the object") { model.uiState.value.createdObjectId == "o9" }
        val body = server.of(create).single().json()
        assertEquals("Hello", body.get("title").asString)
        assertEquals("Done", body.getAsJsonObject("values").get("status").asString)
        assertTrue(server.sent.none { sent -> sent.path.startsWith("p1/-/objects/o9/values/") })
    }

    @Test
    fun `a refused value makes nothing and keeps the form`() {
        server.refuse(create, "Status is too long")
        val model = model()
        model.createObject("card", "Hello", initialValues = mapOf("status" to "x".repeat(10)))
        finished(model)
        assertEquals("Status is too long", model.uiState.value.createError?.message)
        assertNull(model.uiState.value.createdObjectId)
        assertNull(model.uiState.value.made)
    }

    @Test
    fun `a failed upload is said, and Create again sends only it to the object already made`() {
        val model = model()
        val good = file("good.txt")
        val bad = file("bad.txt")
        model.createObject("card", "Hello", uris = listOf(good, bad))
        finished(model)
        assertEquals(1, model.uiState.value.failedAttachments)
        assertEquals("File refused", model.uiState.value.createError?.message)
        assertEquals("o9", model.uiState.value.made)
        assertNull(model.uiState.value.createdObjectId)
        assertEquals(2, server.of(upload).size)

        refusing = false
        model.createObject("card", "Hello", uris = listOf(good, bad))
        until("the object") { model.uiState.value.createdObjectId == "o9" }
        assertEquals("THE POINT: no second object", 1, server.of(create).size)
        val again = server.of(upload).drop(2)
        assertEquals(1, again.size)
        assertTrue(again.single().body.contains("bad.txt"))
        assertFalse(again.single().body.contains("good.txt"))
        assertEquals(0, model.uiState.value.failedAttachments)
        assertNull(model.uiState.value.made)
    }

    @Test
    fun `a failed copy counts each attachment, and is copied once on the retry`() {
        val model = model()
        model.createObject("card", "Hello", copies = listOf("a1", "a2"))
        finished(model)
        assertEquals(2, model.uiState.value.failedAttachments)
        assertEquals("Copy failed", model.uiState.value.createError?.message)
        refusing = false
        model.createObject("card", "Hello", copies = listOf("a1", "a2"))
        until("the object") { model.uiState.value.createdObjectId == "o9" }
        assertEquals(1, server.of(create).size)
        assertEquals(2, server.of(copy).size)
    }

    @Test
    fun `the form says how many attachments did not arrive, and why`() {
        val model = model()
        rule.setContent { CreateObjectScreen(onBack = {}, onCreated = {}, viewModel = model) }
        model.createObject("card", "Hello", uris = listOf(file("bad.txt")), copies = listOf("a1"))
        finished(model)
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("2 attachments could not be added: File refused").fetchSemanticsNodes().isNotEmpty())
    }
}
