// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import android.content.Context
import coil3.SingletonImageLoader
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mochios.android.account.MochiAccount
import org.mochios.android.sync.CalendarsSync
import org.mochios.android.sync.ContactsSync
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.util.AttachmentOpener
import org.mochios.android.util.isServerOrigin
import org.mochios.android.util.originOf
import org.mochios.android.util.serverOrigin
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mochi_session")

@Singleton
class SessionManager internal constructor(
    private val context: Context,
    following: CoroutineScope,
) {
    /**
     * [following] is where [server] is kept current from the store; a test
     * holds it still to see what [setServerUrl] sets by itself.
     */
    @Inject constructor(@ApplicationContext context: Context) :
        this(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_SESSION_COOKIE = stringPreferencesKey("session_cookie")
        // The identity the session signs in, once known: see bindSession.
        private val KEY_SESSION_IDENTITY = stringPreferencesKey("session_identity")
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
        private val KEY_OAUTH_GRANT_VERIFIER = stringPreferencesKey("oauth_grant_verifier")
        private val KEY_OAUTH_GRANT_NONCE = stringPreferencesKey("oauth_grant_nonce")
        private val KEY_OAUTH_GRANT_RETURN_CODE = stringPreferencesKey("oauth_grant_return_code")
        private val KEY_OAUTH_GRANT_RETURN_ERROR = stringPreferencesKey("oauth_grant_return_error")
        private val KEY_BOUND_IDENTITY = stringPreferencesKey("bound_identity")
        // Written by releases before this one and read by none: dropped on the
        // next binding.
        private val KEY_BOUND_SERVER = stringPreferencesKey("bound_server")
        private const val TOKEN_PREFIX = "token_"
        // How each cached token was asked for: Token.form.
        private const val FORM_PREFIX = "form_"
        private const val DEFAULT_SERVER_URL = "https://mochi-os.org"
        private const val KEYSTORE_ALIAS = "mochi.credentials"

        /**
         * What signing out keeps: where the app points and the theme that
         * server gave, which are the server's and not the session's. Emptied,
         * the next sign-in would go to mochi-os.org, with nothing on screen
         * saying so when the server step is already behind the user.
         */
        private val KEPT = setOf(KEY_SERVER_URL, KEY_THEME_HUE, KEY_THEME_CHROMA, KEY_THEME_HUE_BG)
    }

    data class ThemeAnchors(val hue: Float, val chroma: Float, val hueBg: Float)

    private val dataStore = context.dataStore

    // The bearer credentials below are wrapped with an AndroidKeyStore key, so
    // a copy of this DataStore file is not a usable session. The layout is
    // unchanged: only the values are wrapped, and a value written before this
    // existed still reads (see Sealed.open).
    private val sealed = Sealed(Keystore(KEYSTORE_ALIAS))

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

    private val held = MutableStateFlow(runBlocking { dataStore.data.first()[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL })

    /**
     * The server address, held in memory: read once as this is made, then
     * kept current from the store, and set as [setServerUrl] writes. For the
     * image pipeline and the players, which ask on the main thread for every
     * request, and for anything else that needs it without a suspension.
     */
    val server: StateFlow<String> = held.asStateFlow()

    init {
        following.launch {
            serverUrl.collect { address -> held.value = address }
        }
    }

    val isAuthenticated: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SESSION_COOKIE] != null
    }

    val currentToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SESSION_COOKIE]?.let { sealed.open(it) }
    }

    /**
     * Point the client at [url], stored as its origin ([serverOrigin]): a
     * path, query or fragment typed or pasted with it is dropped. A different
     * origin invalidates the session cookie and every per-app token, dropped
     * in the same transaction. "Different" is an origin comparison, so a
     * trailing slash or explicit :443 is not a sign-out.
     *
     * @throws IllegalArgumentException for an address that does not parse or
     *   carries a user name or password.
     */
    suspend fun setServerUrl(url: String) {
        val server = requireNotNull(serverOrigin(url)) { "not a server address" }
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
                prefs.remove(stringPreferencesKey("$FORM_PREFIX$app"))
            }
            prefs.remove(KEY_TOKEN_NAMES)
            prefs.remove(KEY_SESSION_COOKIE)
            prefs.remove(KEY_SESSION_IDENTITY)
            // The binding names an identity on the old server. Left in place, a
            // surviving AccountManager row is adopted and points us back at it.
            prefs.remove(KEY_BOUND_IDENTITY)
        }
        held.value = server
        if (changed) {
            cookieStore.clear()
            sealed.forget()
            forgetAccount()
        }
    }

    /**
     * Keep [cookie] as the session, minted for [identity] when that is known:
     * an account switched to names its own, and a sign-in learns it from the
     * identity read that follows ([bindSession]). A copy of the session held
     * in memory from an earlier response goes: on the same server it would
     * ride the next request in place of this one.
     */
    suspend fun saveSession(cookie: String, identity: String? = null) {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_COOKIE] = sealed.seal(cookie)
            if (identity != null) prefs[KEY_SESSION_IDENTITY] = identity else prefs.remove(KEY_SESSION_IDENTITY)
        }
        cookieStore.replaceAll { _, cookies -> cookies.filterNot { held -> held.name == "session" }.toMutableList() }
    }

    /** The server's renewal of the session held: the same account's, so its identity stands. */
    private suspend fun renewSession(cookie: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_COOKIE] = sealed.seal(cookie)
        }
    }

    /**
     * Bind the session held, [session] as it was read, to [identity], the
     * account the server says it signs in. False when it was kept for another
     * account, or has been replaced since it was read.
     */
    suspend fun bindSession(session: String, identity: String): Boolean {
        var bound = false
        dataStore.edit { prefs ->
            val held = prefs[KEY_SESSION_COOKIE]?.let { sealed.open(it) }
            if (held != session) return@edit
            val owner = prefs[KEY_SESSION_IDENTITY]
            if (owner != null && owner != identity) return@edit
            prefs[KEY_SESSION_IDENTITY] = identity
            bound = true
        }
        return bound
    }

    /** Identity (network-unique entity ID) this app is currently bound to. */
    val boundIdentity: Flow<String?> = dataStore.data.map { it[KEY_BOUND_IDENTITY] }

    suspend fun setBoundAccount(identity: String) {
        var previous: String? = null
        dataStore.edit { prefs ->
            previous = prefs[KEY_BOUND_IDENTITY]
            prefs[KEY_BOUND_IDENTITY] = identity
            prefs.remove(KEY_BOUND_SERVER)
        }
        // An account adopted from a sibling install arrives without a sign-out
        // here, so what was held for the one before it goes now.
        if (previous != null && previous != identity) forgetAccount()
    }

    /**
     * Forget what the device holds for the account before: the item each app
     * last had open, which the next account would be sent to and not find;
     * the image cache, which serves a held image by its address without
     * asking the server, so the next account would be shown the previous
     * one's avatars and attachments without the server deciding whether it
     * may see them; and the documents downloaded to open or share, which the
     * opener hands to a viewer again without asking.
     */
    private suspend fun forgetAccount() {
        LastViewedStore.forget(context)
        // A cache that cannot be cleared must not stop the sign-out around it.
        runCatching {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            withContext(Dispatchers.IO) { loader.diskCache?.clear() }
        }
        runCatching { withContext(Dispatchers.IO) { AttachmentOpener.forget(context) } }
    }

    suspend fun getBoundIdentity(): String? = dataStore.data.first()[KEY_BOUND_IDENTITY]

    /**
     * [app]'s cached token, or null when none is held or the one held was
     * asked for otherwise than [Token.form] says it is now: every reader then
     * goes without it, and the next mint replaces it.
     */
    suspend fun getToken(app: String): String? {
        val prefs = dataStore.data.first()
        val form = prefs[stringPreferencesKey("$FORM_PREFIX$app")] ?: Token.legacy(app)
        if (form != Token.form(app)) return null
        return prefs[stringPreferencesKey("$TOKEN_PREFIX$app")]?.let { sealed.open(it) }
    }

    fun getTokenBlocking(app: String): String? {
        return runBlocking { getToken(app) }
    }

    /**
     * Forget the cached JWT for [app] so the next caller mints a fresh one.
     * Used when the server rejects the token as expired.
     */
    suspend fun clearToken(app: String) {
        sealed.forget()
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("$TOKEN_PREFIX$app"))
            prefs.remove(stringPreferencesKey("$FORM_PREFIX$app"))
            val names = prefs[KEY_TOKEN_NAMES]?.toMutableSet() ?: mutableSetOf()
            names.remove(app)
            prefs[KEY_TOKEN_NAMES] = names
        }
    }

    /**
     * Cache [jwt] as [app]'s token, asked for as [form] says ([Token.form]);
     * null records none, as a token cached before forms were recorded has.
     */
    suspend fun saveToken(app: String, jwt: String, form: String? = Token.form(app)) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("$TOKEN_PREFIX$app")] = sealed.seal(jwt)
            if (form != null) prefs[stringPreferencesKey("$FORM_PREFIX$app")] = form
            else prefs.remove(stringPreferencesKey("$FORM_PREFIX$app"))
            val names = prefs[KEY_TOKEN_NAMES]?.toMutableSet() ?: mutableSetOf()
            names.add(app)
            prefs[KEY_TOKEN_NAMES] = names
        }
    }

    /**
     * Sign out: the session, every app token, the binding and any ceremony in
     * flight go, with the account's synced contacts and calendars; the server
     * address and its theme stay ([KEPT]).
     */
    suspend fun clearAll() {
        val identity = dataStore.data.first()[KEY_BOUND_IDENTITY]
        dataStore.edit { prefs ->
            for (key in prefs.asMap().keys - KEPT) prefs.remove(key)
        }
        // Drop the in-memory cookies too, or the stale `session` rides the next
        // request and the rolling Set-Cookie re-persists what was just cleared.
        cookieStore.clear()
        sealed.forget()
        forgetAccount()
        // Logout in this app shouldn't tear down OTHER apps' bindings —
        // remove only the account this app was bound to. Its synced contacts
        // and calendars go first, while the account still names them.
        if (identity != null) {
            withContext(Dispatchers.IO) {
                ContactsSync.account(context, identity)?.let { ContactsSync.disable(context, it, logout = true) }
                CalendarsSync.account(context, identity)?.let { CalendarsSync.disable(context, it, logout = true) }
            }
            MochiAccount.remove(context, identity)
        }
    }
    /** Records an outstanding sign-in ceremony. The nonce is written in the same edit
     *  as the verifier, so a return can never see one without the other. */
    suspend fun saveOAuthVerifier(verifier: String, nonce: String? = null) {
        dataStore.edit { prefs ->
            prefs[KEY_OAUTH_VERIFIER] = sealed.seal(verifier)
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
        val verifier = prefs[KEY_OAUTH_VERIFIER]?.let { sealed.open(it) }
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
            prefs[KEY_OAUTH_LINK_VERIFIER] = sealed.seal(verifier)
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
        val verifier = prefs[KEY_OAUTH_LINK_VERIFIER]?.let { sealed.open(it) }
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

    // OAuth GRANT ceremony: a capability granted to a connected account, such
    // as calendar access on a Google account. The same shape as the link: the
    // server lands the grant only at the exchange, against this verifier plus
    // the app's Bearer.

    suspend fun saveOAuthGrantVerifier(verifier: String, nonce: String? = null) {
        dataStore.edit { prefs ->
            prefs[KEY_OAUTH_GRANT_VERIFIER] = sealed.seal(verifier)
            if (nonce != null) prefs[KEY_OAUTH_GRANT_NONCE] = nonce else prefs.remove(KEY_OAUTH_GRANT_NONCE)
        }
    }

    suspend fun oauthGrantCeremony(): OAuthCeremony {
        val prefs = dataStore.data.first()
        return OAuthCeremony(
            hasVerifier = !prefs[KEY_OAUTH_GRANT_VERIFIER].isNullOrBlank(),
            nonce = prefs[KEY_OAUTH_GRANT_NONCE],
        )
    }

    suspend fun consumeOAuthGrantVerifier(): String? {
        val prefs = dataStore.data.first()
        val verifier = prefs[KEY_OAUTH_GRANT_VERIFIER]?.let { sealed.open(it) }
        if (verifier != null) {
            dataStore.edit { p ->
                p.remove(KEY_OAUTH_GRANT_VERIFIER)
                p.remove(KEY_OAUTH_GRANT_NONCE)
            }
        }
        return verifier
    }

    val oauthGrantReturn: Flow<Pair<String?, String?>> = dataStore.data.map { prefs ->
        prefs[KEY_OAUTH_GRANT_RETURN_CODE] to prefs[KEY_OAUTH_GRANT_RETURN_ERROR]
    }

    suspend fun setOAuthGrantReturn(code: String?, error: String?) {
        dataStore.edit { prefs ->
            if (code != null) prefs[KEY_OAUTH_GRANT_RETURN_CODE] = code else prefs.remove(KEY_OAUTH_GRANT_RETURN_CODE)
            if (error != null) prefs[KEY_OAUTH_GRANT_RETURN_ERROR] = error else prefs.remove(KEY_OAUTH_GRANT_RETURN_ERROR)
        }
    }

    suspend fun clearOAuthGrantReturn() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_OAUTH_GRANT_RETURN_CODE)
            prefs.remove(KEY_OAUTH_GRANT_RETURN_ERROR)
        }
    }

    /**
     * The identity this device is signed in as, or null when unbound. Blocking
     * for the framework-instantiated callers (broadcast receivers) that have no
     * coroutine to read the flow from.
     */
    fun getBoundIdentityBlocking(): String? {
        return runBlocking {
            dataStore.data.first()[KEY_BOUND_IDENTITY]
        }
    }

    /**
     * The server address. It no longer blocks: it reads [server], and is kept
     * for the callers not yet moved to that.
     */
    fun getServerUrlBlocking(): String = server.value

    private fun getSessionCookieBlocking(): String? {
        return runBlocking {
            dataStore.data.first()[KEY_SESSION_COOKIE]?.let { sealed.open(it) }
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
        isServerOrigin(url, server.value)

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
            // signed-in. Core sets the cookie at sign-in with a fixed 365-day
            // lifetime, and older cores also sent it again on each request;
            // one with the value already held is not written again, since
            // each write is a fresh seal and a rewrite of the credential file.
            if (sessionCookie != null) {
                val held = getSessionCookieBlocking()
                if (held != null && held != sessionCookie.value) {
                    runBlocking { renewSession(sessionCookie.value) }
                }
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
