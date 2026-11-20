// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import org.mochios.android.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.mochios.android.auth.SessionManager
import org.mochios.android.push.DeviceStore
import org.mochios.android.util.isServerOrigin
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppContext

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InvalidationInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ServerInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebSocketClient

/** How many requests the shared client sends the server at once; OkHttp's own default is 5. */
internal const val REQUESTS_PER_HOST = 16

/**
 * Rewrite [url]'s origin to [serverUrl], leaving path, query and fragment
 * alone; returns [url] unchanged when already there or when [serverUrl] is
 * unparseable. Retrofit pins its baseUrl at construction, so only the path
 * prefix is its own.
 */
internal fun retargetToServer(url: HttpUrl, serverUrl: String): HttpUrl {
    val server = serverUrl.toHttpUrlOrNull() ?: return url
    if (isServerOrigin(url, serverUrl)) return url
    return url.newBuilder()
        .scheme(server.scheme)
        .host(server.host)
        .port(server.port)
        .build()
}

/**
 * Interceptor form of [retargetToServer]; the supplier is read per request
 * rather than captured.
 */
internal fun serverInterceptor(server: () -> String): Interceptor = Interceptor { chain ->
    val request = chain.request()
    val target = retargetToServer(request.url, server())
    chain.proceed(if (target == request.url) request else request.newBuilder().url(target).build())
}

/** The error code core's own 401 answers carry. */
internal const val AUTHENTICATION_REQUIRED = "authentication_required"

/**
 * Clears the session, through [clear], on a 401 that is about the session the
 * request rode, read through [session], and only while that session is still
 * the one held. Only a JSON 401 counts: an HTML one is a captive portal or a
 * proxy.
 *
 * Not every 401 is about the session. An app's action answers whatever status
 * it chooses, and one that relays a peer server's answer can relay that
 * server's 401, so a 401 on an app's path may be another server's word; taken
 * as core's, any server's operator could sign a reader out, and take their
 * synced contacts and calendars with them. So a 401 from core's own paths
 * (`/_/...`, which no app and no peer answers) carrying core's
 * [AUTHENTICATION_REQUIRED] signs out; any other is put to core as `GET
 * /_/identity` with the same session, and signs out only when that answers
 * 401 too. Otherwise the session stays, and the request's own error reaches
 * its caller. The body alone is not enough: an app relaying a peer's answer
 * can carry core's code in it.
 */
internal fun invalidationInterceptor(
    session: suspend () -> String?,
    clear: suspend () -> Unit,
): Interceptor = Interceptor { chain ->
    val request = chain.request()
    val sessionAtSend = runBlocking { session() }
    val response = chain.proceed(request)
    if (response.code != 401 || sessionAtSend == null) return@Interceptor response
    if (response.header("Content-Type")?.contains("application/json") != true) return@Interceptor response
    // The body is read whole, so the answer can still be handed on after the
    // identity read below reuses the call.
    val type = response.body.contentType()
    val bytes = response.body.bytes()
    val answer = response.newBuilder().body(bytes.toResponseBody(type)).build()
    val core = request.url.encodedPath.startsWith("/_/")
    val dead = when {
        core && authenticationRequired(bytes) -> true
        request.url.encodedPath == IDENTITY_PATH -> true
        else -> identityRefused(chain, request)
    }
    if (dead && runBlocking { session() } == sessionAtSend) {
        runBlocking { clear() }
    }
    answer
}

/** Core's identity read, which every client signed in can make and no app answers. */
private const val IDENTITY_PATH = "/_/identity"

/** Whether [body] is a JSON object whose `error` is [AUTHENTICATION_REQUIRED]. */
private fun authenticationRequired(body: ByteArray): Boolean =
    runCatching {
        JsonParser.parseString(String(body, Charsets.UTF_8)).asJsonObject.get("error")?.asString == AUTHENTICATION_REQUIRED
    }.getOrDefault(false)

/**
 * Whether core refuses the session [request] rode: `GET /_/identity` on the
 * same origin, carrying the session cookie and nothing of the request's own,
 * answers 401. An answer that does not come, or any other, keeps the session.
 */
private fun identityRefused(chain: Interceptor.Chain, request: Request): Boolean {
    val identity = Request.Builder()
        .url(request.url.newBuilder().encodedPath(IDENTITY_PATH).query(null).fragment(null).build())
        .header("Accept", "application/json")
        .apply { request.header("Device")?.let { device -> header("Device", device) } }
        .build()
    return try {
        chain.proceed(identity).use { answer -> answer.code == 401 }
    } catch (e: IOException) {
        false
    }
}

/**
 * A builder for talking to a server other than the bound one - another
 * account's server, or a host that is not a Mochi server at all.
 *
 * Deliberately fresh rather than `okHttpClient.newBuilder()`: that copies the
 * whole interceptor chain, and its outermost member rewrites every request's
 * origin to the *bound* server. A call built that way for account B's server
 * arrives at account A's instead, carrying B's credential - see
 * [serverInterceptor]. It also carries no cookie jar, so a session for one
 * origin cannot be replayed to another.
 */
internal fun foreignClient(): OkHttpClient.Builder = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    // The server content-negotiates: without this header it returns an HTML
    // error page instead of {error, message} JSON.
    .addInterceptor(Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder().header("Accept", "application/json").build()
        )
    })

/**
 * The app's HTTP logger, built the same way everywhere it is installed.
 *
 * Install it on debug builds only: every level from BASIC up writes the full
 * request URL to logcat, and HEADERS adds the `Authorization` bearer and the
 * cookies with it.
 *
 * Where it goes matters as much as the level. OkHttp skips network
 * interceptors on a WebSocket call, so a logger added with
 * `addNetworkInterceptor` never sees a handshake - a client that opens
 * WebSockets installs it with `addInterceptor` instead, last, so it still logs
 * the request every interceptor above it has rewritten.
 *
 * @param level how much of each exchange to write; BASIC is the request and
 *   response lines alone.
 * @return a fresh interceptor, safe to share between clients.
 */
internal fun httpLogging(
    level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC,
): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
    this.level = level
}

@Module
@InstallIn(SingletonComponent::class)
object ApiClient {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        // Mochi's SQLite layer stores booleans as 0/1 integers, which surface
        // unchanged in JSON responses. Tolerate that on the client so Kotlin
        // `Boolean` fields parse cleanly.
        val boolAdapter = object : TypeAdapter<Boolean>() {
            override fun read(reader: JsonReader): Boolean = when (reader.peek()) {
                JsonToken.NUMBER -> reader.nextInt() != 0
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.STRING -> reader.nextString().let { it == "true" || it == "1" }
                JsonToken.NULL -> { reader.nextNull(); false }
                else -> { reader.skipValue(); false }
            }
            override fun write(writer: JsonWriter, value: Boolean?) {
                if (value == null) writer.nullValue() else writer.value(value)
            }
        }
        // Strict parsing on purpose: lenient mode silently accepts malformed
        // JSON, which turns a server contract break into a wrong value instead
        // of an error the contract check can chase.
        return GsonBuilder()
            .registerTypeAdapter(Boolean::class.java, boolAdapter)
            .registerTypeAdapter(Boolean::class.javaPrimitiveType, boolAdapter)
            .create()
    }

    @Provides
    @Singleton
    @AuthInterceptor
    fun provideAuthInterceptor(sessionManager: SessionManager, deviceStore: DeviceStore): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val appName = original.header("X-Mochi-App")
            val builder = original.newBuilder()

            if (appName != null) {
                builder.removeHeader("X-Mochi-App")
                val token = sessionManager.getTokenBlocking(appName)
                if (token != null) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            // Every request says which device it comes from, so the server can
            // key what this device shows itself. Unconditional: the module
            // clients all derive from this one and set their own Authorization,
            // and the server interceptor sends every request here to the bound
            // server, so the id cannot reach another host.
            builder.header("Device", deviceStore.id())

            builder.build().let { chain.proceed(it) }
        }
    }

    /**
     * Signs out on a 401 about this session; see [invalidationInterceptor].
     * 403 is left alone: it can mean a missing app token or a real permission
     * failure.
     */
    @Provides
    @Singleton
    @InvalidationInterceptor
    fun provideInvalidationInterceptor(sessionManager: SessionManager): Interceptor =
        invalidationInterceptor(
            session = { sessionManager.currentToken.first() },
            clear = { sessionManager.clearAll() },
        )

    @Provides
    @Singleton
    @ServerInterceptor
    fun provideServerInterceptor(sessionManager: SessionManager): Interceptor =
        serverInterceptor { sessionManager.server.value }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ServerInterceptor serverInterceptor: Interceptor,
        @AuthInterceptor authInterceptor: Interceptor,
        @InvalidationInterceptor invalidationInterceptor: Interceptor,
        sessionManager: SessionManager
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Every request goes to the one server, so OkHttp's default of 5
            // at a time per host held back the rest of a screen's reads: an
            // opened project item's object and eight tabs went out five, then
            // four. Shared by every module's client, which derive from this one.
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = REQUESTS_PER_HOST })
            // Outermost, so the auth and logging interceptors and the cookie
            // jar all see the corrected origin. Clients that fetch foreign
            // hosts (AssetHttp, NominatimService, UpdateChecker) must keep
            // building from a fresh builder.
            .addInterceptor(serverInterceptor)
            // The server content-negotiates: without this header it returns an
            // HTML error page instead of {error, message} JSON. Set here so
            // every module's client, built via okHttpClient.newBuilder(),
            // inherits it.
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            })
            .addInterceptor(authInterceptor)
            .addInterceptor(invalidationInterceptor)
            // A call marked @Slow waits as long as the server may run it.
            .addInterceptor(slowInterceptor)
            .cookieJar(sessionManager.cookieJar)

        // At the network layer, so it logs what every interceptor above it
        // left behind. This client opens no WebSockets; MochiWebSocket logs
        // its handshakes on its own, for the reason [httpLogging] gives.
        if (BuildConfig.DEBUG) {
            builder.addNetworkInterceptor(httpLogging())
        }

        return builder.build()
    }

    /**
     * WebSockets get their own client. The keepalive ping is theirs alone, and
     * more importantly [MochiWebSocket][org.mochios.android.websocket.MochiWebSocket]
     * is handed an absolute server URL per subscription - the push distributor
     * holds one per identity, on whichever server that identity lives - so the
     * bound-server retarget in [provideOkHttpClient] would send one identity's
     * bearer token to another identity's server. The cookie jar stays: app
     * modules subscribe with no token and authenticate by session, and the jar
     * only releases the session to its own origin.
     */
    @Provides
    @Singleton
    @WebSocketClient
    fun provideWebSocketClient(sessionManager: SessionManager): OkHttpClient =
        foreignClient()
            .pingInterval(5, TimeUnit.MINUTES)
            .cookieJar(sessionManager.cookieJar)
            .apply {
                // An application interceptor is the only logging hook a
                // WebSocket call keeps; see [httpLogging].
                if (BuildConfig.DEBUG) {
                    addInterceptor(httpLogging())
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson, sessionManager: SessionManager): Retrofit {
        return createRetrofit(sessionManager.server.value, okHttpClient, gson)
    }

    fun createRetrofit(serverUrl: String, okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
