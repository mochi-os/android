package org.mochi.android.auth

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
        dataStore.edit { prefs ->
            prefs.clear()
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
