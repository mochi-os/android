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
 * OkHttp client for fetching media assets. Requests to the user's own Mochi
 * server mirror the API clients' auth — the session cookie plus the per-app
 * bearer token — so Coil and ExoPlayer can load session-gated images (avatars,
 * chat / feed attachments, video frames) the same way the REST clients do.
 *
 * The app name is the URL's first path segment (`/chat/...` → "chat"), which is
 * the key [SessionManager.getTokenBlocking] expects, so a single client serves
 * every feature's assets. Deliberately omits the session-invalidation
 * interceptor: a transient image / video 401 must not sign the user out.
 *
 * Requests to any other host (publisher CDNs behind RSS post images) carry no
 * Mochi auth and are dressed as a browser instead: image CDNs behind bot
 * mitigation reject bare library fetches — Cloudflare 403s the Japan Times'
 * images without a Referer, whatever the user agent — so send the image's own
 * origin as Referer plus the browser UA / Accept pair the feeds source WebView
 * already impersonates for the same reason.
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
                // Whole origin, not the host: on a host match alone
                // `https://host:8443/feeds/…` and `http://host/feeds/…` were
                // treated as ours and handed the Feeds JWT — and for an avatar
                // path the token went into the query string too, so it would
                // survive in logs and redirects. isServerOrigin also fails
                // closed on an unset or unparseable server URL.
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
 * Auth headers for fetching a session-gated asset [url] outside OkHttp (e.g.
 * `MediaMetadataRetriever`). Mirrors [AssetHttpModule]: the per-app bearer
 * token (keyed on the URL's first path segment) plus the session cookie.
 *
 * Nothing is returned for a URL that is not on the user's server. The app name
 * is taken from the first path segment, so without this an attacker-chosen URL
 * shaped like `https://attacker.example/feeds/video.mp4` collected the user's
 * Feeds JWT — and feeds really does hand absolute URLs here, since an RSS
 * enclosure's own address is used verbatim for video frame extraction.
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
