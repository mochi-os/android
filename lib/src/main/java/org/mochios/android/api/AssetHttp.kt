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
import org.mochios.android.util.isServerOrigin
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AssetHttpClient

/**
 * Media-asset client. Own-server requests carry the session cookie and per-app
 * token but no invalidation interceptor - an image 401 must not sign the user
 * out. Foreign hosts get browser UA/Accept/Referer, which bot-mitigated CDNs
 * require.
 */
@Module
@InstallIn(SingletonComponent::class)
object AssetHttpModule {

    // Chrome-on-Android UA, matching the feeds source WebView's impersonation.
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/127.0.0.0 Mobile Safari/537.36"

    private const val BROWSER_IMAGE_ACCEPT = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"

    @Provides
    @Singleton
    @AssetHttpClient
    fun provideAssetHttpClient(sessionManager: SessionManager): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(sessionManager.cookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                // Whole origin, not the host: a host-only match handed the app
                // token
                // to `http://host` and `host:8443`, and into the avatar query string.
                if (!isServerOrigin(request.url, sessionManager.getServerUrlBlocking())) {
                    return@addInterceptor chain.proceed(
                        request.newBuilder()
                            .header("User-Agent", BROWSER_USER_AGENT)
                            .header("Accept", BROWSER_IMAGE_ACCEPT)
                            .header("Referer", "${request.url.scheme}://${request.url.host}/")
                            .build()
                    )
                }
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
 * Auth headers for a session-gated asset fetched outside OkHttp. Empty unless
 * [url] is on the user's server - feeds passes absolute RSS URLs here, and
 * `attacker.example/feeds/...` would otherwise collect the Feeds token.
 */
fun assetAuthHeaders(sessionManager: SessionManager, url: String): Map<String, String> {
    val httpUrl = url.toHttpUrlOrNull() ?: return emptyMap()
    if (!isServerOrigin(httpUrl, sessionManager.getServerUrlBlocking())) return emptyMap()
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
