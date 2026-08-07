// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import org.mochios.android.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.isServerOrigin
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

/**
 * Rewrite [url]'s origin — scheme, host and port — to the one named by
 * [serverUrl], leaving the path, query and fragment alone. Returns [url]
 * unchanged when it is already on that origin, or when [serverUrl] is
 * unparseable (nothing to retarget to, so leave the request as the caller
 * built it).
 *
 * Retrofit fixes its `baseUrl` when the instance is built, and every Retrofit
 * here is a `@Singleton` constructed from the server URL that happened to be
 * stored at first injection — for the core one that is before the user has had
 * a chance to choose a server at all. This lets the pinned `baseUrl` supply
 * only the path prefix (`/feeds/`, `/crm/`, …) while the origin follows the
 * user's current server.
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
 * Interceptor form of [retargetToServer]. Takes the server as a supplier so it
 * is read per request rather than captured, and so ServerRetargetTest can drive
 * it without an Android context.
 */
internal fun serverInterceptor(server: () -> String): Interceptor = Interceptor { chain ->
    val request = chain.request()
    val target = retargetToServer(request.url, server())
    chain.proceed(if (target == request.url) request else request.newBuilder().url(target).build())
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
        return GsonBuilder()
            .setLenient()
            .registerTypeAdapter(Boolean::class.java, boolAdapter)
            .registerTypeAdapter(Boolean::class.javaPrimitiveType, boolAdapter)
            .create()
    }

    @Provides
    @Singleton
    @AuthInterceptor
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor {
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

            builder.build().let { chain.proceed(it) }
        }
    }

    /**
     * Watches every response for 401 (authentication failed). When seen we
     * tear down the local session and the matching AccountManager record so
     * the app falls back to the login screen — instead of looping forever
     * with a dead cookie. 403 is *not* treated as session-dead: it can mean
     * "authenticated but missing app token" (which the per-app JWT
     * interceptors handle) or "authenticated but no permission" (legitimate).
     *
     * Three guards keep transient failures from signing the user out:
     *  - the request must have carried the session we currently hold (the
     *    cookie jar attaches exactly the stored value, so a null-at-send
     *    session means the 401 indicts nothing);
     *  - the stored session must be unchanged when the response arrives, so
     *    an in-flight request from before a login/renewal can't clear the
     *    new session it never rode;
     *  - the response must be JSON — the client sends Accept:
     *    application/json, so real Mochi 401s are JSON; an HTML 401 is a
     *    captive portal or proxy answering for an unreachable server, which
     *    says nothing about the session.
     */
    @Provides
    @Singleton
    @InvalidationInterceptor
    fun provideInvalidationInterceptor(sessionManager: SessionManager): Interceptor {
        return Interceptor { chain ->
            val sessionAtSend = runBlocking { sessionManager.currentToken.first() }
            val response = chain.proceed(chain.request())
            if (response.code == 401 && sessionAtSend != null) {
                val json = response.header("Content-Type")?.contains("application/json") == true
                val sessionNow = runBlocking { sessionManager.currentToken.first() }
                if (json && sessionNow == sessionAtSend) {
                    runBlocking { sessionManager.clearAll() }
                }
            }
            response
        }
    }

    @Provides
    @Singleton
    @ServerInterceptor
    fun provideServerInterceptor(sessionManager: SessionManager): Interceptor =
        serverInterceptor { sessionManager.getServerUrlBlocking() }

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
            // Outermost, so the auth, invalidation and logging interceptors
            // below — and the cookie jar, which OkHttp consults in
            // BridgeInterceptor after every application interceptor — all see
            // the corrected origin rather than the one Retrofit was pinned to.
            // Every module's client is built via okHttpClient.newBuilder(), so
            // all sixteen Retrofit instances inherit this; the clients that
            // deliberately fetch foreign hosts (AssetHttp, NominatimService,
            // UpdateChecker) build from a fresh OkHttpClient.Builder() and must
            // keep doing so, or an RSS image URL would be rewritten to the
            // user's server.
            .addInterceptor(serverInterceptor)
            // Ask the server for JSON errors on every request. The Mochi server
            // content-negotiates (Action.error in core/server/actions.go):
            // without this header it serves an HTML error page, which the client
            // then rendered as raw markup — the "ugly web-page error". With it,
            // errors come back as structured {error, message} JSON with a
            // localised message. Set here (ahead of the auth interceptor) so
            // every module's client — each built via okHttpClient.newBuilder() —
            // inherits it, which is why the per-module copies were removed.
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            })
            .addInterceptor(authInterceptor)
            .addInterceptor(invalidationInterceptor)
            .cookieJar(sessionManager.cookieJar)

        // Debug builds only. BASIC still logs the full request URL, and OkHttp
        // preserves application interceptors on WebSocket handshakes, so on a
        // release build this wrote every URL - and any credential or capability
        // carried in a query string - into logcat. MochiWebSocket already moved
        // its token to a header for exactly this reason; that was the workaround,
        // and this is the cause.
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson, sessionManager: SessionManager): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking()
        return createRetrofit(serverUrl, okHttpClient, gson)
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
