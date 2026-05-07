package org.mochios.android.ui.theme

import org.mochios.android.auth.SessionManager
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import javax.inject.Inject
import javax.inject.Singleton

/** Response from GET /settings/-/user/preferences */
private data class PreferencesResponse(
    val preferences: Map<String, String>?,
    val themes: List<ThemeData>?,
    val default_theme: String?
)

private data class ThemeData(
    val id: String?,
    val hue: Double?,
    val chroma: Double?,
    val hue_bg: Double?
)

private interface SettingsApi {
    @GET("settings/-/user/preferences/data")
    suspend fun getPreferences(
        @Header("Authorization") token: String
    ): Response<PreferencesResponse>
}

/**
 * Fetches the user's color theme from the settings app and caches it locally.
 * The theme is resolved by matching the user's preference (or system default)
 * against the available themes list.
 */
@Singleton
class ThemeRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val retrofit: Retrofit
) {
    private val settingsApi: SettingsApi by lazy {
        retrofit.create(SettingsApi::class.java)
    }

    /**
     * Fetch the user's theme from the server and cache it in DataStore.
     * Call after authentication is complete.
     */
    suspend fun fetchAndCacheTheme() {
        try {
            // Get a settings app token
            val tokenResponse = retrofit.create(TokenApi::class.java)
                .fetchToken(TokenRequest("settings"))
            val tokenBody = tokenResponse.body() ?: return
            val jwt = tokenBody.token

            // Fetch preferences with the settings token
            val response = settingsApi.getPreferences("Bearer $jwt")
            val data = response.body() ?: return

            // Resolve which theme is active
            val themeId = data.preferences?.get("theme")
                ?: data.default_theme
                ?: return
            val theme = data.themes?.find { it.id == themeId } ?: return
            val hue = theme.hue?.toFloat() ?: return
            val chroma = theme.chroma?.toFloat() ?: return
            val hueBg = theme.hue_bg?.toFloat() ?: return

            sessionManager.saveTheme(hue, chroma, hueBg)
        } catch (_: Exception) {
            // Theme fetch is best-effort — cached values or defaults apply
        }
    }
}

/** Reuse the existing token endpoint format */
private data class TokenRequest(val app: String)
private data class TokenResponse(val token: String)

private interface TokenApi {
    @retrofit2.http.POST("_/token")
    suspend fun fetchToken(
        @retrofit2.http.Body request: TokenRequest
    ): Response<TokenResponse>
}
