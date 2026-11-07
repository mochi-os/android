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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
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
 * Qualifies the media client that never sends credentials, for images whose
 * address someone else wrote: markdown and HTML in posts, comments and wiki
 * pages.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnonymousAssetHttpClient

/**
 * The header marking an asset request as one of this app's own addresses (see
 * [owned]). The asset client sends the session and an app token to the user's
 * server only with it, and takes it off before the request leaves.
 */
const val OWNED = "Mochi-Owned"

/**
 * Whether [address] is one of this app's own: server-relative ("/feeds/..."),
 * as every address the app builds from its own routes is, and as the user's
 * server gives the ones it serves. An absolute URL is never one, even on the
 * user's server: it may be a peer's choice - a link, a page's image, a feed's
 * enclosure - and with the session and the token of the app its path named, a
 * request for it would run any of that app's actions as the user. A backslash
 * or a control character, which no address the app builds holds, makes it none.
 */
fun owned(address: String): Boolean =
    address.startsWith("/") && !address.startsWith("//") && address.none { it == '\\' || it.isISOControl() }

/** [address] resolved against [server]: an [owned] one onto it, any other as given. */
fun resolved(server: String, address: String): String =
    if (owned(address)) server.trimEnd('/') + address else address

/**
 * Media-asset client, with no invalidation interceptor - an image 401 must not
 * sign the user out. A request marked [OWNED] for the user's server carries the
 * session cookie and the token of the app its path names; any other request for
 * the user's server carries neither, so an address someone else chose reaches
 * the server as an anonymous one. Foreign hosts get browser UA/Accept/Referer,
 * which bot-mitigated CDNs require.
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
        assetClient(
            server = sessionManager::getServerUrlBlocking,
            token = sessionManager::getTokenBlocking,
            jar = sessionManager.cookieJar,
        )

    /**
     * The asset client over [server], the user's server's origin, [token],
     * an app's token by name, and [jar], the session's cookie jar.
     */
    internal fun assetClient(server: () -> String, token: (String) -> String?, jar: CookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(foreign(jar) { url -> isServerOrigin(url, server()) })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().removeHeader(OWNED).build()
                // Whole origin, not the host: a host-only match handed the app
                // token to `http://host` and to `host:8443`.
                if (isServerOrigin(request.url, server())) {
                    return@addInterceptor chain.proceed(request)
                }
                chain.proceed(browser(request))
            }
            // The credentials go on hop by hop, and only on the hop for the
            // call's own marked address. A redirect is a request for an address
            // the answer chose - a commenter's avatar route redirects to the icon
            // URL they set - and a header put on above would follow it there,
            // the Cookie to any host and the token anywhere on the server.
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val call = chain.call().request()
                if (call.header(OWNED) == null || request.url != call.url || !isServerOrigin(request.url, server())) {
                    return@addNetworkInterceptor chain.proceed(request)
                }
                val app = request.url.pathSegments.firstOrNull { segment -> segment.isNotEmpty() }
                chain.proceed(session(authorised(request, app?.let(token)), jar.loadForRequest(request.url)))
            }
            .build()

    /**
     * Media client for addresses a peer wrote into a body. A post, comment or
     * wiki page can hold `![](/someapp/-/some-action)`; fetched with the session
     * cookie and app token, every reader's phone would call that action as the
     * reader. So no cookie jar and no token for any host, the user's own server
     * included.
     */
    @Provides
    @Singleton
    @AnonymousAssetHttpClient
    fun provideAnonymousAssetHttpClient(
        @AssetHttpClient assetHttpClient: OkHttpClient,
        sessionManager: SessionManager,
    ): OkHttpClient = anonymousClient(sessionManager::getServerUrlBlocking, assetHttpClient)

    /**
     * The anonymous client over [server], the user's server's origin, sharing
     * [shared]'s connections and dispatcher. It is built new, not from
     * [shared]'s builder, which would keep the network interceptor that adds
     * the session: a client for addresses a peer chose holds no code that can
     * send it, and drops an [OWNED] mark before the request leaves.
     */
    internal fun anonymousClient(server: () -> String, shared: OkHttpClient): OkHttpClient =
        OkHttpClient.Builder()
            .connectionPool(shared.connectionPool)
            .dispatcher(shared.dispatcher)
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().removeHeader(OWNED).build()
                if (isServerOrigin(request.url, server())) {
                    return@addInterceptor chain.proceed(request)
                }
                chain.proceed(browser(request))
            }
            .build()

    /** [request] as a browser sends it, for a foreign host. */
    private fun browser(request: Request): Request =
        request.newBuilder()
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", BROWSER_IMAGE_ACCEPT)
            .header("Referer", "${request.url.scheme}://${request.url.host}/")
            .build()
}

/** The header [AssetHttpClient] reads: [OWNED] for one of the app's own addresses ([owned]), nothing for any other. */
fun assetHeaders(address: String): Map<String, String> = if (owned(address)) mapOf(OWNED to "1") else emptyMap()

/**
 * The asset client's cookie jar: [jar] for foreign hosts, whose cookies a
 * bot-mitigated CDN may want back, and nothing for the user's server, whose
 * session goes only with a request marked [OWNED], added by the client itself.
 * A cookie the user's server sets on an image answer is not kept: the API
 * client's answers renew the session.
 */
internal fun foreign(jar: CookieJar, server: (HttpUrl) -> Boolean): CookieJar = object : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!server(url)) jar.saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (server(url)) emptyList() else jar.loadForRequest(url)
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

/** [request] with [cookies], the session's, in its `Cookie` header, or unchanged when there are none. */
internal fun session(request: Request, cookies: List<Cookie>): Request =
    if (cookies.isEmpty()) {
        request
    } else {
        request.newBuilder()
            .header("Cookie", cookies.joinToString("; ") { cookie -> "${cookie.name}=${cookie.value}" })
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
