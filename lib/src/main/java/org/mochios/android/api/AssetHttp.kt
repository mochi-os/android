// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.mochios.android.auth.SessionManager
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AssetHttpClient

/**
 * OkHttp client for fetching session-gated media assets (avatars, chat / feed
 * attachments, video frames). Mirrors the API clients' auth — the session
 * cookie plus the per-app bearer token — so Coil and ExoPlayer can load images
 * and stream video the same way the REST clients do.
 *
 * The app name is the URL's first path segment (`/chat/...` → "chat"), which is
 * the key [SessionManager.getTokenBlocking] expects, so a single client serves
 * every feature's assets. Deliberately omits the session-invalidation
 * interceptor: a transient image / video 401 must not sign the user out.
 */
@Module
@InstallIn(SingletonComponent::class)
object AssetHttpModule {

    @Provides
    @Singleton
    @AssetHttpClient
    fun provideAssetHttpClient(sessionManager: SessionManager): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(sessionManager.cookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                val app = request.url.pathSegments.firstOrNull { segment -> segment.isNotEmpty() }
                val token = app?.let { sessionManager.getTokenBlocking(it) }
                val authed = if (token != null) {
                    val builder = request.newBuilder()
                        .header("Authorization", "Bearer $token")
                    // Avatar endpoints authenticate via the `token` query param —
                    // they redirect to a file URL that drops the Authorization
                    // header — so pass the token there too.
                    if (request.url.pathSegments.lastOrNull() == "avatar") {
                        builder.url(
                            request.url.newBuilder()
                                .setQueryParameter("token", token)
                                .build()
                        )
                    }
                    builder.build()
                } else {
                    request
                }
                chain.proceed(authed)
            }
            .build()
}

/**
 * Lets non-Hilt call sites (composables in the shared UI layer) reach the
 * [AssetHttpClient] via `EntryPointAccessors.fromApplication(...)`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AssetHttpEntryPoint {

    @AssetHttpClient
    fun assetHttpClient(): OkHttpClient
}

/**
 * Auth headers for fetching a session-gated asset [url] outside OkHttp (e.g.
 * `MediaMetadataRetriever`). Mirrors [AssetHttpModule]: the per-app bearer
 * token (keyed on the URL's first path segment) plus the session cookie.
 */
fun assetAuthHeaders(sessionManager: SessionManager, url: String): Map<String, String> {
    val httpUrl = url.toHttpUrlOrNull() ?: return emptyMap()
    val headers = HashMap<String, String>()
    httpUrl.pathSegments.firstOrNull { segment -> segment.isNotEmpty() }
        ?.let { app -> sessionManager.getTokenBlocking(app) }
        ?.let { token -> headers["Authorization"] = "Bearer $token" }
    val cookies = sessionManager.cookieJar.loadForRequest(httpUrl)
    if (cookies.isNotEmpty()) {
        headers["Cookie"] = cookies.joinToString("; ") { cookie -> "${cookie.name}=${cookie.value}" }
    }
    return headers
}
