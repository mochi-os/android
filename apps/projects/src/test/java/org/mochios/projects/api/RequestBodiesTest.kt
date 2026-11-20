// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.projects.repository.ProjectsRepository
import org.mochios.projects.util.HIERARCHY_ROOT
import org.mochios.projects.util.hierarchyParameter
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * What the server reads from each call that can send an empty value. Core
 * reads an empty form field as absent but an empty JSON string as "", so a
 * cleared value only arrives in a JSON body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RequestBodiesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()

    /** Each request: its path, content type and body. */
    private val asked = ConcurrentLinkedQueue<Triple<String, String, String>>()

    /** The `data` the server answers for a path. */
    private var answers: Map<String, String> = emptyMap()

    private lateinit var repository: ProjectsRepository

    @Before
    fun begin() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/projects/").substringBefore("?")
                asked.add(Triple(path, request.getHeader("Content-Type").orEmpty(), request.body.readUtf8()))
                return MockResponse().setBody("""{"data": ${answers[path] ?: "{}"}}""")
            }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        repository = ProjectsRepository(api, FileStore(context), context, SessionManager(context))
    }

    @After
    fun end() = server.shutdown()

    /** The JSON body sent to [path], which must have gone as JSON. */
    private fun body(path: String): JsonObject {
        val (_, type, body) = asked.single { (asked, _, _) -> asked == path }
        assertTrue("$path went as $type", type.startsWith("application/json"))
        return JsonParser.parseString(body).asJsonObject
    }

    @Test
    fun `detaching an object sends an empty parent`() {
        runBlocking { repository.updateObject("p1", "o1", parent = "") }
        val sent = body("p1/-/objects/o1/update")
        assertEquals("THE POINT: the top level arrives as \"\"", "", sent["parent"]?.asString)
    }

    @Test
    fun `a top-level reorder sends an empty scope`() {
        runBlocking { repository.moveObject("p1", "o1", field = "", rank = 2, scopeParent = "") }
        val sent = body("p1/-/objects/o1/move")
        assertEquals("", sent["scope"]?.asString)
        assertFalse("an untouched value is left out, so the server keeps it", sent.has("value"))
    }

    @Test
    fun `top level only goes as the empty parents string, as the web sends it`() {
        runBlocking {
            repository.setHierarchy("p1", "task", hierarchyParameter(listOf(HIERARCHY_ROOT)))
        }
        assertEquals("", body("p1/-/classes/task/hierarchy/set")["parents"]?.asString)
    }

    @Test
    fun `a project's description emptied goes as an empty string`() {
        runBlocking { repository.updateProject("p1", name = "Board", description = "", prefix = "B") }
        assertEquals("", body("p1/-/update")["description"]?.asString)
    }

    @Test
    fun `an option's colour and icon cleared go as empty strings`() {
        runBlocking { repository.updateOption("p1", "task", "status", "open", name = "Open", colour = "", icon = "") }
        val sent = body("p1/-/classes/task/fields/status/options/open/update")
        assertEquals("", sent["colour"]?.asString)
        assertEquals("", sent["icon"]?.asString)
    }

    @Test
    fun `an object's values are read beside it`() {
        answers = mapOf("p1/-/objects/o1" to """{"object": {"id": "o1"}, "values": {"status": "done"}}""")
        val fetched = runBlocking { repository.getObject("p1", "o1") }
        assertEquals("done", fetched.values["status"])
    }

    @Test
    fun `a new field is read as the server sends it`() {
        answers = mapOf("p1/-/classes/task/fields/create" to """{"id": "due", "name": "Due", "fieldtype": "date", "rank": 3}""")
        val field = runBlocking { repository.createField("p1", "task", "Due", "date") }
        assertEquals("due", field.id)
    }

    @Test
    fun `a new option is read as the server sends it`() {
        answers = mapOf("p1/-/classes/task/fields/status/options/create" to """{"id": "late", "name": "Late", "colour": "", "rank": 4}""")
        val option = runBlocking { repository.createOption("p1", "task", "status", "Late") }
        assertEquals("late", option.id)
    }

    @Test
    fun `a new view is read as the server sends it`() {
        answers = mapOf("p1/-/views/create" to """{"id": "v2", "name": "Board", "viewtype": "board"}""")
        val view = runBlocking { repository.createView("p1", "Board", "board") }
        assertEquals("v2", view.id)
    }

    @Test
    fun `a new merge request is read as the server sends it`() {
        answers = mapOf(
            "p1/-/objects/o1/requests/create" to
                """{"id": "r1", "object": "o1", "status": "open", "title": "Fix", "draft": false, "created": 1, "updated": 1}"""
        )
        val request = runBlocking { repository.createRequest("p1", "o1", "repo", "fix", "main", "Fix") }
        assertEquals("r1", request.id)
    }
}
