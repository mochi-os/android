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
import org.mochios.android.account.MochiAccount
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

    suspend fun setServerUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url.trimEnd('/')
        }
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

    val cookieJar: CookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies.toMutableList()
            val sessionCookie = cookies.find { it.name == "session" }
            if (sessionCookie != null) {
                runBlocking { saveSession(sessionCookie.value) }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val stored = cookieStore[url.host]?.toMutableList() ?: mutableListOf()
            val sessionValue = getSessionCookieBlocking()
            if (sessionValue != null) {
                val hasSession = stored.any { it.name == "session" }
                if (!hasSession) {
                    val cookie = Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name("session")
                        .value(sessionValue)
                        .build()
                    stored.add(cookie)
                }
            }
            return stored
        }
    }
}
