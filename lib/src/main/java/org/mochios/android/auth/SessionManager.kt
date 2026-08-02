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
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_SESSION_COOKIE = stringPreferencesKey("session_cookie")
        private val KEY_TOKEN_NAMES = stringSetPreferencesKey("token_names")
        private val KEY_THEME_HUE = stringPreferencesKey("theme_hue")
        private val KEY_THEME_CHROMA = stringPreferencesKey("theme_chroma")
        private val KEY_THEME_HUE_BG = stringPreferencesKey("theme_hue_bg")
        private val KEY_OAUTH_VERIFIER = stringPreferencesKey("oauth_verifier")
        private val KEY_OAUTH_RETURN_CODE = stringPreferencesKey("oauth_return_code")
        private val KEY_OAUTH_RETURN_ERROR = stringPreferencesKey("oauth_return_error")
        private val KEY_OAUTH_LINK_PROVIDER = stringPreferencesKey("oauth_link_provider")
        private val KEY_OAUTH_LINK_ERROR = stringPreferencesKey("oauth_link_error")
        private val KEY_OAUTH_LINK_PENDING = stringPreferencesKey("oauth_link_pending")
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
     * Point the client at [url].
     *
     * Moving to a *different* server invalidates every credential we hold: the
     * session cookie and the per-app JWTs were issued by the previous server,
     * are meaningless to the new one, and — since the jar attaches the session
     * to whichever origin is configured — would otherwise be handed straight to
     * it. So they are dropped in the same transaction that stores the new URL.
     *
     * The two callers that adopt an account belonging to another server
     * ([adoptSharedSessionIfMissing] and AppBootstrapViewModel.adopt) set the
     * server first and write that account's session immediately after, so the
     * clear never races credentials they are about to store.
     *
     * "Different" is an origin comparison, not a string one, so retyping the
     * same server with a trailing slash, different case or an explicit :443
     * does not sign the user out.
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
            // The binding names an identity on the OLD server, so it is as
            // stale as the tokens above. Leaving it lets a surviving
            // AccountManager row be adopted afterwards and setServerUrl called
            // back to the previous server, silently undoing this change.
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
        // Drop the in-memory cookies too. Otherwise the stale `session` cookie
        // still rides on the next request (e.g. an FCM token refresh fired by
        // tearDown()), and the server's rolling Set-Cookie re-persists the very
        // session we just cleared — flipping isAuthenticated back to true.
        cookieStore.clear()
        // Logout in this app shouldn't tear down OTHER apps' bindings —
        // remove only the account this app was bound to.
        if (identity != null) MochiAccount.remove(context, identity)
    }

    /**
     * Reserved for future reconciliation hooks. Today we trust the local
     * session as the source of truth for authentication; AccountManager is
     * a sharing mechanism, not the canonical store. Cross-app logouts are
     * detected through the runtime [MochiAccount.watch] listener, not via
     * a startup state diff (which can't distinguish a genuine logout from
     * a missing-because-never-written record).
     */
    suspend fun validateLocalAgainstAccount(): Boolean = true

    /**
     * If we have no local session, look for a sibling Mochi account whose
     * server matches what this app is already bound to (or the most recently
     * registered account, when this is a fresh install with no binding).
     * Returns the snapshot if one was adopted, or null.
     */
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

    suspend fun saveOAuthVerifier(verifier: String) {
        dataStore.edit { prefs ->
            prefs[KEY_OAUTH_VERIFIER] = verifier
        }
    }

    /**
     * Whether an OAuth ceremony this client started is still outstanding.
     *
     * The verifier is written at `/begin` and consumed at exchange, so its
     * presence is the only local evidence that a `mochi:oauth-return` belongs
     * to us. Checked without consuming, so asking does not itself invalidate
     * the ceremony.
     */
    suspend fun hasOAuthVerifier(): Boolean =
        !dataStore.data.first()[KEY_OAUTH_VERIFIER].isNullOrBlank()

    suspend fun consumeOAuthVerifier(): String? {
        val prefs = dataStore.data.first()
        val verifier = prefs[KEY_OAUTH_VERIFIER]
        if (verifier != null) {
            dataStore.edit { p -> p.remove(KEY_OAUTH_VERIFIER) }
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

    /** Separate channel from the login OAuth return — the link flow's server
     *  callback sends back `?oauth_linked=<provider>` or `?oauth_error=...`
     *  rather than a code to exchange. SecurityViewModel listens for these
     *  to know when to refresh the OAuth-identities list. */
    val oauthLinkReturn: Flow<Pair<String?, String?>> = dataStore.data.map { prefs ->
        prefs[KEY_OAUTH_LINK_PROVIDER] to prefs[KEY_OAUTH_LINK_ERROR]
    }

    suspend fun setOAuthLinkReturn(provider: String?, error: String?) {
        dataStore.edit { prefs ->
            if (provider != null) prefs[KEY_OAUTH_LINK_PROVIDER] = provider else prefs.remove(KEY_OAUTH_LINK_PROVIDER)
            if (error != null) prefs[KEY_OAUTH_LINK_ERROR] = error else prefs.remove(KEY_OAUTH_LINK_ERROR)
        }
    }

    suspend fun clearOAuthLinkReturn() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_OAUTH_LINK_PROVIDER)
            prefs.remove(KEY_OAUTH_LINK_ERROR)
        }
    }

    /**
     * Records that this client started a link ceremony for [provider].
     *
     * The link flow has no exchange step — the server completes the link and
     * redirects — so unlike a login there is no verifier consumed at the end to
     * stand as evidence the ceremony was ours. This marker is that evidence,
     * and it is written to its own key: the login verifier is consumed by the
     * exchange, and a link borrowing it would leave one behind that no code
     * path clears, permanently satisfying [hasOAuthVerifier] and with it the
     * login return's own gate.
     */
    suspend fun saveOAuthLinkPending(provider: String) {
        dataStore.edit { prefs ->
            prefs[KEY_OAUTH_LINK_PENDING] = provider
        }
    }

    /** Consumes the outstanding link ceremony, returning the provider it was for. */
    suspend fun consumeOAuthLinkPending(): String? {
        val provider = dataStore.data.first()[KEY_OAUTH_LINK_PENDING]
        if (provider != null) {
            dataStore.edit { prefs -> prefs.remove(KEY_OAUTH_LINK_PENDING) }
        }
        return provider
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
     * In-memory per-origin cookie cache, cleared on [clearAll].
     *
     * Keyed by whole origin rather than host. Keyed by host, two different
     * origins sharing a hostname shared a bucket, so a `session` cookie set by
     * `http://host` or `host:8443` was replayed to `https://host` — and it also
     * satisfied the "already has a session" test below, suppressing the real
     * stored session in favour of whatever that other origin had set.
     */
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    /** True when [url] is the user's own Mochi server. See [isServerOrigin]. */
    private fun isServerOrigin(url: HttpUrl): Boolean =
        isServerOrigin(url, getServerUrlBlocking())

    /**
     * Carries the Mochi session cookie to the user's own server, and nowhere
     * else. Both directions are gated on [isServerOrigin], because this jar is
     * shared by clients that deliberately fetch foreign hosts — the asset
     * client behind Coil loads RSS post images from arbitrary publisher
     * domains (see AssetHttpModule), and assetAuthHeaders reuses
     * [loadForRequest] for MediaMetadataRetriever.
     *
     * Without the gate a hostile feed image leaked the live session cookie to
     * its host (full account impersonation), and a `Set-Cookie: session=…` in
     * that host's reply overwrote the stored session — logging the user out, or
     * pinning the session to a value the host chose.
     *
     * Cookies a foreign host sets for itself are still cached and replayed to
     * that same host; the cache is keyed by host, so that is ordinary
     * per-origin behaviour and stays working for CDNs that need it.
     */
    val cookieJar: CookieJar = object : CookieJar {

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Safe to cache before the origin check now that the bucket is the
            // origin: a foreign origin's cookies can only ever be replayed to
            // that same origin.
            cookieStore[originOf(url)] = cookies.toMutableList()
            if (!isServerOrigin(url)) return
            val sessionCookie = cookies.find { it.name == "session" }
            // Only *renew* an existing session — never resurrect one that
            // sign-out or a 401 has cleared. Login establishes the session
            // explicitly via AuthRepository.extractSessionCookie, so gating on
            // an existing token here is safe and stops a stray authenticated
            // call (e.g. an FCM token refresh) from flipping us back signed-in.
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
