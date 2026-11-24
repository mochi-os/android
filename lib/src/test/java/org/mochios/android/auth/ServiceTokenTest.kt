// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.ApiClient
import org.mochios.android.i18n.FakeKeystore
import org.mochios.android.notifications.NotificationsApi
import org.mochios.android.notifications.NotificationsModule
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.android.push.mintPushToken
import org.mochios.android.websocket.BoundSocketSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The notifications token - the bell's, the notifications screens', the
 * registrations' and the push channel's - is asked for by the notifications
 * service, so a domain's route covering /notifications does not make it the
 * route target's; and an older core, which reads only the path, still answers
 * the notifications app's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServiceTokenTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var auth: AuthRepository
    private val requests = mutableListOf<RecordedRequest>()

    /**
     * Answers /_/token as core resolves it: by `service` when [reads] it and
     * the body names one, otherwise by `app` as a path - to [route]'s target
     * when a domain's route covers /notifications, to the root app at "".
     * Every other path answers an empty success.
     */
    private fun core(reads: Boolean, route: Boolean = false) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                if (request.path != "/_/token") {
                    val empty = when (request.path) {
                        "/notifications/-/list" -> """{"data": [], "count": 0}"""
                        "/notifications/-/categories/list" -> """{"data": []}"""
                        else -> """{"data": {}}"""
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(empty)
                }
                val body = JsonParser.parseString(request.body.clone().readUtf8()).asJsonObject
                val service = body.get("service")?.asString.orEmpty()
                val app = body.get("app")?.asString
                val id = when {
                    reads && service == "notifications" -> "notifications-app"
                    reads && service != "" -> return MockResponse().setResponseCode(404).setBody("""{"error": "app_not_found"}""")
                    app == null -> return MockResponse().setResponseCode(400).setBody("""{"error": "app_required"}""")
                    route -> "route-target"
                    app == "notifications" -> "notifications-app"
                    app == "" -> "home-app"
                    else -> app
                }
                return MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"token": "$id-token", "app": "$id", "asset": "$id-asset", "identity": {"id": "i1", "name": "John"}}""")
            }
        }
    }

    private fun mints() = requests.filter { it.path == "/_/token" }

    @Before
    fun begin() {
        FakeKeystore.install()
        server = MockWebServer().apply { start() }
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(server.url("/").toString())
            session.saveSession("s1")
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(ApiClient.provideGson()))
            .build()
        auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
    }

    @After
    fun end() = server.shutdown()

    @Test
    fun `the notifications token is asked for by its service, with its path beside it`() {
        core(reads = true)
        runBlocking { auth.fetchToken("notifications", fresh = true) }
        val body = JsonParser.parseString(mints().single().body.readUtf8()).asJsonObject
        assertEquals("notifications", body.get("service")?.asString)
        assertEquals("notifications", body.get("app")?.asString)
    }

    @Test
    fun `on a domain routed over its path the notifications app's token is still the one held`() {
        core(reads = true, route = true)
        assertEquals("notifications-app-token", runBlocking { auth.fetchToken("notifications", fresh = true) }.getOrThrow())
    }

    @Test
    fun `an older core, which reads only the path, answers the notifications app's token`() {
        core(reads = false)
        assertEquals("notifications-app-token", runBlocking { auth.fetchToken("notifications", fresh = true) }.getOrThrow())
    }

    @Test
    fun `the bell's and the notifications screens' calls carry the scoped token`() {
        core(reads = true, route = true)
        runBlocking { auth.fetchToken("notifications", fresh = true) }
        val api = NotificationsModule.provideNotificationsRetrofit(OkHttpClient(), ApiClient.provideGson(), session)
            .create(NotificationsApi::class.java)
        val repository = NotificationsRepository(api)
        runBlocking {
            repository.list()
            repository.count()
            repository.markRead("n1")
            repository.markAllRead()
            repository.categories()
        }
        val calls = requests.filter { it.path != "/_/token" }
        assertEquals(
            listOf("/notifications/-/list", "/notifications/-/count", "/notifications/-/read", "/notifications/-/read/all", "/notifications/-/categories/list"),
            calls.map { it.path },
        )
        for (call in calls) {
            assertEquals(call.path, "Bearer notifications-app-token", call.getHeader("Authorization"))
        }
    }

    @Test
    fun `the badge socket opens with the scoped token`() {
        core(reads = true, route = true)
        runBlocking { auth.fetchToken("notifications", fresh = true) }
        assertEquals("notifications-app-token", runBlocking { BoundSocketSession(session, auth).token("notifications") })
    }

    @Test
    fun `the push channel's token is asked for by the service, on the account's own session`() {
        core(reads = true)
        runBlocking { mintPushToken(OkHttpClient(), ApiClient.provideGson(), server.url("/").toString(), "account-session") }
        val mint = mints().single()
        val body = JsonParser.parseString(mint.body.readUtf8()).asJsonObject
        assertEquals("notifications", body.get("service")?.asString)
        assertEquals("notifications", body.get("app")?.asString)
        assertEquals("session=account-session", mint.getHeader("Cookie"))
    }

    @Test
    fun `on a domain routed over its path the push channel holds the notifications app's token`() {
        core(reads = true, route = true)
        val token = runBlocking { mintPushToken(OkHttpClient(), ApiClient.provideGson(), server.url("/").toString(), "account-session") }
        assertEquals("notifications-app-token", token)
    }

    @Test
    fun `an older core answers the push channel the notifications app's token`() {
        core(reads = false)
        val token = runBlocking { mintPushToken(OkHttpClient(), ApiClient.provideGson(), server.url("/").toString(), "account-session") }
        assertEquals("notifications-app-token", token)
    }

    /** A token that lives a year, as core mints them. */
    private fun year(mark: String): String {
        val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
        val payload = """{"exp": ${System.currentTimeMillis() / 1000 + 365L * 86400}, "mark": "$mark"}"""
        return encoder.encodeToString("{}".toByteArray()) + "." + encoder.encodeToString(payload.toByteArray()) + ".sig"
    }

    /** Cached by a build that asked for every token by path, and recorded nothing of it. */
    @Test
    fun `a notifications token cached from a mint by path is minted again, once`() {
        core(reads = true, route = true)
        runBlocking { session.saveToken("notifications", year("route"), form = null) }
        assertEquals("not used meanwhile", null, runBlocking { session.getToken("notifications") })
        assertEquals("THE POINT: minted again", "notifications-app-token", runBlocking { auth.fetchToken("notifications") }.getOrThrow())
        assertEquals(1, mints().size)
        assertEquals("and then held", "notifications-app-token", runBlocking { session.getToken("notifications") })
    }

    @Test
    fun `a token asked for by path and cached before is still used`() {
        core(reads = true)
        val held = year("feeds")
        runBlocking { session.saveToken("feeds", held, form = null) }
        assertEquals(held, runBlocking { auth.fetchToken("feeds") }.getOrThrow())
        assertEquals(0, mints().size)
    }

    @Test
    fun `an app whose token a page uses is asked for by its path alone`() {
        core(reads = true)
        runBlocking { auth.fetchToken("feeds", fresh = true) }
        runBlocking { auth.fetchToken("home", fresh = true) }
        assertEquals(listOf("""{"app":"feeds"}""", """{"app":""}"""), mints().map { it.body.readUtf8() })
    }
}
