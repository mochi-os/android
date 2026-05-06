package org.mochi.android.i18n

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.mochi.android.auth.SessionManager
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import javax.inject.Inject
import javax.inject.Singleton

private data class PrefsResponse(val preferences: Map<String, String>?)

private interface SettingsApi {
    @GET("settings/-/user/preferences/data")
    suspend fun getPreferences(@Header("Authorization") token: String): Response<PrefsResponse>
}

/**
 * Fetches the user's `language` preference from the settings app and stores
 * it in [LanguageStore] for the next process launch. Apply the change in the
 * current process by calling [LocaleHelper.apply] and `Activity.recreate()`.
 */
@Singleton
class LanguageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val retrofit: Retrofit
) {
    private val api: SettingsApi by lazy { retrofit.create(SettingsApi::class.java) }

    suspend fun fetchAndStore(): String? {
        val token = sessionManager.getToken("settings") ?: return null
        return try {
            val resp = api.getPreferences("Bearer $token")
            val tag = resp.body()?.preferences?.get("language") ?: return null
            LanguageStore.set(context, tag)
            tag
        } catch (_: Exception) {
            null
        }
    }
}
