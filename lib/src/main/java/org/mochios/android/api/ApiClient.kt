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
     * Clears the session on a 401 that the session we still hold actually rode,
     * and only for a JSON body - an HTML 401 is a captive portal or proxy. 403
     * is left alone: it can mean a missing app token or a real permission
     * failure.
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
            .cookieJar(sessionManager.cookieJar)

        // Debug only: BASIC logs the full URL, including any credential in a
        // query string, and OkHttp keeps application interceptors on WebSocket
        // handshakes.
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
