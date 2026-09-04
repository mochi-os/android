// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.combine
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mochios.android.account.MochiAccount
import org.mochios.android.util.isServerOrigin
import org.mochios.android.util.originOf
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mochi_session")

@Singleton
class SessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_SESSION_COOKIE = stringPreferencesKey("session_cookie")
        private val KEY_TOKEN_NAMES = stringSetPreferencesKey("token_names")
        private val KEY_THEME_HUE = stringPreferencesKey("theme_hue")
        private val KEY_THEME_CHROMA = stringPreferencesKey("theme_chroma")
        private val KEY_THEME_HUE_BG = stringPreferencesKey("theme_hue_bg")
        private val KEY_OAUTH_VERIFIER = stringPreferencesKey("oauth_verifier")
        private val KEY_OAUTH_NONCE = stringPreferencesKey("oauth_nonce")
        private val KEY_OAUTH_RETURN_CODE = stringPreferencesKey("oauth_return_code")
        private val KEY_OAUTH_RETURN_ERROR = stringPreferencesKey("oauth_return_error")
        // A LINK ceremony keeps its own slots: shared ones would let the login
        // collector consume a link return, or either overwrite the other.
        private val KEY_OAUTH_LINK_VERIFIER = stringPreferencesKey("oauth_link_verifier")
        private val KEY_OAUTH_LINK_NONCE = stringPreferencesKey("oauth_link_nonce")
        private val KEY_OAUTH_LINK_RETURN_CODE = stringPreferencesKey("oauth_link_return_code")
        private val KEY_OAUTH_LINK_RETURN_ERROR = stringPreferencesKey("oauth_link_return_error")
        private val KEY_BOUND_IDENTITY = stringPreferencesKey("bound_identity")
        private val KEY_BOUND_SERVER = stringPreferencesKey("bound_server")
        private const val TOKEN_PREFIX = "token_"
        private const val DEFAULT_SERVER_URL = "https://mochi-os.org"
    }

    data class ThemeAnchors(val hue: Float, val chroma: Float, val hueBg: Float)

    private val dataStore = context.dataStore

    val themeAnchors: Flow<ThemeAnchors?> = dataStore.data.map { prefs ->
        val hue = prefs[KEY_THEME_HUE]?.toFloatOrNull() ?: return@map null
        val chroma = prefs[KEY_THEME_CHROMA]?.toFloatOrNull() ?: return@map null
        val hueBg = prefs[KEY_THEME_HUE_BG]?.toFloatOrNull() ?: return@map null
        ThemeAnchors(hue, chroma, hueBg)
    }

    suspend fun saveTheme(hue: Float, chroma: Float, hueBg: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_HUE] = hue.toString()
            prefs[KEY_THEME_CHROMA] = chroma.toString()
            prefs[KEY_THEME_HUE_BG] = hueBg.toString()
        }
    }

    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val isAuthenticated: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SESSION_COOKIE] != null
    }

    val currentToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SESSION_COOKIE]
    }

    /**
     * Point the client at [url]. A different origin invalidates the session
     * cookie and every per-app token, dropped in the same transaction.
     * "Different" is an origin comparison, so a trailing slash or explicit :443
     * is not a sign-out.
     */
    suspend fun setServerUrl(url: String) {
        val server = url.trimEnd('/')
        var changed = false
        dataStore.edit { prefs ->
            val previous = prefs[KEY_SERVER_URL]
            prefs[KEY_SERVER_URL] = server
            if (previous == null) return@edit
            val parsed = server.toHttpUrlOrNull()
            if (parsed != null && isServerOrigin(parsed, previous)) return@edit
            changed = true
            for (app in prefs[KEY_TOKEN_NAMES].orEmpty()) {
                prefs.remove(stringPreferencesKey("$TOKEN_PREFIX$app"))
            }
            prefs.remove(KEY_TOKEN_NAMES)
            prefs.remove(KEY_SESSION_COOKIE)
            // The binding names an identity on the old server. Left in place, a
            // surviving AccountManager row is adopted and points us back at it.
            prefs.remove(KEY_BOUND_IDENTITY)
            prefs.remove(KEY_BOUND_SERVER)
        }
        if (changed) cookieStore.clear()
    }

    suspend fun saveSession(cookie: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_COOKIE] = cookie
        }
    }

    /** Identity (network-unique entity ID) this app is currently bound to. */
    val boundIdentity: Flow<String?> = dataStore.data.map { it[KEY_BOUND_IDENTITY] }

    /** Server this app's bound account belongs to. */
    val boundServer: Flow<String?> = dataStore.data.map { it[KEY_BOUND_SERVER] }

    suspend fun setBoundAccount(identity: String, server: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BOUND_IDENTITY] = identity
            prefs[KEY_BOUND_SERVER] = server
        }
    }

    suspend fun getBoundIdentity(): String? = dataStore.data.first()[KEY_BOUND_IDENTITY]

    suspend fun getToken(app: String): String? {
        val prefs = dataStore.data.first()
        return prefs[stringPreferencesKey("$TOKEN_PREFIX$app")]
    }

    fun getTokenBlocking(app: String): String? {
        return runBlocking { getToken(app) }
    }

    /**
     * Forget the cached JWT for [app] so the next caller mints a fresh one.
     * Used when the server rejects the token as expired.
     */
    suspend fun clearToken(app: String) {
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("$TOKEN_PREFIX$app"))
            val names = prefs[KEY_TOKEN_NAMES]?.toMutableSet() ?: mutableSetOf()
            names.remove(app)
            prefs[KEY_TOKEN_NAMES] = names
        }
    }

    suspend fun saveToken(app: String, jwt: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("$TOKEN_PREFIX$app")] = jwt
            val names = prefs[KEY_TOKEN_NAMES]?.toMutableSet() ?: mutableSetOf()
            names.add(app)
            prefs[KEY_TOKEN_NAMES] = names
        }
    }

    suspend fun clearAll() {
        val identity = dataStore.data.first()[KEY_BOUND_IDENTITY]
        dataStore.edit { prefs ->
            prefs.clear()
        }
        // Drop the in-memory cookies too, or the stale `session` rides the next
        // request and the rolling Set-Cookie re-persists what was just cleared.
        cookieStore.clear()
        // Logout in this app shouldn't tear down OTHER apps' bindings —
        // remove only the account this app was bound to.
        if (identity != null) MochiAccount.remove(context, identity)
    }

    suspend fun adoptSharedSessionIfMissing(): MochiAccount.Snapshot? {
        val prefs = dataStore.data.first()
        if (prefs[KEY_SESSION_COOKIE] != null) return null
        val boundServer = prefs[KEY_BOUND_SERVER]
        val candidate = if (boundServer != null) {
            MochiAccount.byServer(context, boundServer)
        } else {
            MochiAccount.first(context)
        } ?: return null
        setServerUrl(candidate.server)
        saveSession(candidate.session)
        setBoundAccount(candidate.identity, candidate.server)
        return candidate
    }

    /** Records an outstanding sign-in ceremony. The nonce is written in the same edit
     *  as the verifier, so a return can never see one without the other. */
    suspend fun saveOAuthVerifier(verifier: String, nonce: String? = null) {
        dataStore.edit { prefs ->
            prefs[KEY_OAUTH_VERIFIER] = verifier
            if (nonce != null) prefs[KEY_OAUTH_NONCE] = nonce else prefs.remove(KEY_OAUTH_NONCE)
        }
    }

    /**
     * The outstanding ceremony's verifier presence and nonce, from one snapshot
     * so they always describe the same ceremony. Reading does not consume: a
     * consuming check would let an injected return drop the genuine callback
     * behind it.
     */
    data class OAuthCeremony(val hasVerifier: Boolean, val nonce: String?)

    suspend fun oauthCeremony(): OAuthCeremony {
        val prefs = dataStore.data.first()
        return OAuthCeremony(
            hasVerifier = !prefs[KEY_OAUTH_VERIFIER].isNullOrBlank(),
            nonce = prefs[KEY_OAUTH_NONCE],
        )
    }

    suspend fun consumeOAuthVerifier(): String? {
        val prefs = dataStore.data.first()
        val verifier = prefs[KEY_OAUTH_VERIFIER]
        if (verifier != null) {
            // The nonce goes with it: it authenticates the return for THIS
            // ceremony, and one left behind would be checked against the next.
            dataStore.edit { p ->
                p.remove(KEY_OAUTH_VERIFIER)
                p.remove(KEY_OAUTH_NONCE)
            }
        }
        return verifier
    }

    val oauthReturn: Flow<Pair<String?, String?>> = dataStore.data.map { prefs ->
        prefs[KEY_OAUTH_RETURN_CODE] to prefs[KEY_OAUTH_RETURN_ERROR]
    }

    suspend fun setOAuthReturn(code: String?, error: String?) {
        dataStore.edit { prefs ->
            if (code != null) prefs[KEY_OAUTH_RETURN_CODE] = code else prefs.remove(KEY_OAUTH_RETURN_CODE)
            if (error != null) prefs[KEY_OAUTH_RETURN_ERROR] = error else prefs.remove(KEY_OAUTH_RETURN_ERROR)
        }
    }

    suspend fun clearOAuthReturn() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_OAUTH_RETURN_CODE)
            prefs.remove(KEY_OAUTH_RETURN_ERROR)
        }
    }

    // OAuth LINK ceremony: same shape as the sign-in one above. The server
    // writes the identity link only at the exchange, against this verifier plus
    // our Bearer, so a browser holding the callback cannot write it.

    suspend fun saveOAuthLinkVerifier(verifier: String, nonce: String? = null) {
        dataStore.edit { prefs ->
            prefs[KEY_OAUTH_LINK_VERIFIER] = verifier
            if (nonce != null) prefs[KEY_OAUTH_LINK_NONCE] = nonce else prefs.remove(KEY_OAUTH_LINK_NONCE)
        }
    }

    suspend fun oauthLinkCeremony(): OAuthCeremony {
        val prefs = dataStore.data.first()
        return OAuthCeremony(
            hasVerifier = !prefs[KEY_OAUTH_LINK_VERIFIER].isNullOrBlank(),
            nonce = prefs[KEY_OAUTH_LINK_NONCE],
        )
    }

    suspend fun consumeOAuthLinkVerifier(): String? {
        val prefs = dataStore.data.first()
        val verifier = prefs[KEY_OAUTH_LINK_VERIFIER]
        if (verifier != null) {
            dataStore.edit { p ->
                p.remove(KEY_OAUTH_LINK_VERIFIER)
                p.remove(KEY_OAUTH_LINK_NONCE)
            }
        }
        return verifier
    }

    val oauthLinkReturn: Flow<Pair<String?, String?>> = dataStore.data.map { prefs ->
        prefs[KEY_OAUTH_LINK_RETURN_CODE] to prefs[KEY_OAUTH_LINK_RETURN_ERROR]
    }

    suspend fun setOAuthLinkReturn(code: String?, error: String?) {
        dataStore.edit { prefs ->
            if (code != null) prefs[KEY_OAUTH_LINK_RETURN_CODE] = code else prefs.remove(KEY_OAUTH_LINK_RETURN_CODE)
            if (error != null) prefs[KEY_OAUTH_LINK_RETURN_ERROR] = error else prefs.remove(KEY_OAUTH_LINK_RETURN_ERROR)
        }
    }

    suspend fun clearOAuthLinkReturn() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_OAUTH_LINK_RETURN_CODE)
            prefs.remove(KEY_OAUTH_LINK_RETURN_ERROR)
        }
    }

    fun getServerUrlBlocking(): String {
        return runBlocking {
            dataStore.data.first()[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
        }
    }

    private fun getSessionCookieBlocking(): String? {
        return runBlocking {
            dataStore.data.first()[KEY_SESSION_COOKIE]
        }
    }

    /**
     * In-memory per-origin cookie cache, cleared on [clearAll]. Keyed by whole
     * origin:
     * by host alone, a `session` set by `http://host` or `host:8443` reached
     * `https://host`.
     */
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    /** True when [url] is the user's own Mochi server. See [isServerOrigin]. */
    private fun isServerOrigin(url: HttpUrl): Boolean =
        isServerOrigin(url, getServerUrlBlocking())

    /**
     * Carries the Mochi session cookie to the user's own server and nowhere
     * else - this jar is shared with the asset client, which fetches arbitrary
     * publisher hosts. Ungated, a hostile image both received the session and
     * could replace it.
     */
    val cookieJar: CookieJar = object : CookieJar {

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Safe to cache before the origin check now that the bucket is the
            // origin: a foreign origin's cookies can only ever be replayed to
            // that same origin.
            cookieStore[originOf(url)] = cookies.toMutableList()
            if (!isServerOrigin(url)) return
            val sessionCookie = cookies.find { it.name == "session" }
            // Only renew an existing session, never resurrect one that sign-out
            // or a 401 cleared: a stray authenticated call would flip us back
            // signed-in.
            if (sessionCookie != null && getSessionCookieBlocking() != null) {
                runBlocking { saveSession(sessionCookie.value) }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val stored = cookieStore[originOf(url)]?.toMutableList() ?: mutableListOf()
            if (!isServerOrigin(url)) return stored
            val sessionValue = getSessionCookieBlocking()
            if (sessionValue != null) {
                val hasSession = stored.any { it.name == "session" }
                if (!hasSession) {
                    val builder = Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name("session")
                        .value(sessionValue)
                    if (url.isHttps) builder.secure()
                    stored.add(builder.build())
                }
            }
            return stored
        }
    }
}
