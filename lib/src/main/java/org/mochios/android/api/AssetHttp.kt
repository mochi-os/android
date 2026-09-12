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
import okhttp3.Request
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
                // token to `http://host` and to `host:8443`.
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
                chain.proceed(authorised(request, app?.let { sessionManager.getTokenBlocking(it) }))
            }
            .build()
}

/**
 * [request] with [token] attached, or unchanged when there is no token.
 *
 * The credential goes in the `Authorization` header and nowhere else. Avatars
 * used to repeat it in a `token` query parameter, for a redirect to a file URL
 * that would drop the header; core answers them 200 with no redirect, so the
 * copy in the URL only wrote a year-long credential into every access log
 * between here and there. A URL this builds must stay loggable.
 *
 * @param request the asset request, already known to be on the bound server.
 * @param token the app token for that asset's app, null when none is held.
 * @return the request to send.
 */
internal fun authorised(request: Request, token: String?): Request =
    if (token == null) {
        request
    } else {
        request.newBuilder()
            .header("Authorization", "Bearer $token")
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
