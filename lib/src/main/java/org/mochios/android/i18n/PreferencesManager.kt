package org.mochios.android.i18n

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mochios.android.auth.SessionManager
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private data class FullPrefsResponse(val preferences: Map<String, String>?)

private interface PreferencesApi {
    @GET("settings/-/user/preferences/data")
    suspend fun getPreferences(@Header("Authorization") token: String): Response<FullPrefsResponse>

    @retrofit2.http.FormUrlEncoded
    @POST("settings/-/user/preferences/set")
    suspend fun setPreferences(
        @Header("Authorization") token: String,
        @retrofit2.http.FieldMap fields: Map<String, String>,
    ): Response<Map<String, Any>>
}

// Hosts (chat / feeds / forums / projects / …) mint their own app token at
// bootstrap; the settings app's token isn't fetched for them. We mint it
// here on demand so the preferences endpoint, which requires a settings
// token, is reachable from every Mochi Android host.
private data class TokenRequest(val app: String)
private data class TokenResponse(val token: String)

private interface TokenApi {
    @POST("_/token")
    suspend fun fetchToken(@Body request: TokenRequest): Response<TokenResponse>
}

/**
 * Mirror of web's `LocaleProvider`: holds a [StateFlow] of [UserPreferences]
 * resolved against the current device for any `auto` server values.
 *
 * Apps fetch on launch via [refresh]; the UI observes [preferences] and
 * rebuilds [Format] when it changes. There is no live-update path on Android
 * (no shell, no preference-change websocket event); the next launch picks up
 * any changes the user made on the web settings page.
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val retrofit: Retrofit
) {

    private val api: PreferencesApi by lazy { retrofit.create(PreferencesApi::class.java) }
    private val tokenApi: TokenApi by lazy { retrofit.create(TokenApi::class.java) }

    private val _preferences = MutableStateFlow(resolveAuto(emptyMap()))
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    /** Most recent raw server map (pre-auto-resolve). Settings UI needs the
     *  original "" / "auto" / explicit value to render the right select. */
    private var rawPrefs: Map<String, String> = emptyMap()

    fun rawPreferences(): Map<String, String> = rawPrefs

    /** Latest snapshot, for non-Composable callers (e.g. ViewModels). */
    val format: Format get() = Format(_preferences.value)

    /**
     * Persist a single preference to the server and refresh the local
     * [preferences] state. An empty [value] resets the preference back to
     * the server default. Throws on auth/network failure so the caller can
     * surface an error.
     */
    suspend fun setPreference(key: String, value: String) {
        val token = sessionManager.getToken("settings") ?: try {
            tokenApi.fetchToken(TokenRequest("settings")).body()?.token
        } catch (_: Exception) {
            null
        } ?: throw IllegalStateException("settings token unavailable")
        val resp = api.setPreferences("Bearer $token", mapOf(key to value))
        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
        refresh()
    }

    suspend fun refresh() {
        // Cached "settings" token first (saves a round trip when this app
        // happens to be settings itself or has already minted one). Otherwise
        // mint a fresh one from the host's session — every Mochi user has a
        // settings app token available.
        val token = sessionManager.getToken("settings") ?: try {
            tokenApi.fetchToken(TokenRequest("settings")).body()?.token
        } catch (_: Exception) {
            null
        } ?: return
        val resp = try {
            api.getPreferences("Bearer $token")
        } catch (_: Exception) {
            return
        }
        val raw = resp.body()?.preferences ?: return
        rawPrefs = raw
        _preferences.value = resolveAuto(raw)
    }

    private fun resolveAuto(raw: Map<String, String>): UserPreferences {
        val locale = Locale.getDefault()
        val dateFormat = when (val v = raw["date_format"]) {
            null, "", "auto" -> detectDateFormat(locale)
            else -> DateFormat.fromString(v) ?: detectDateFormat(locale)
        }
        val timeFormat = when (val v = raw["time_format"]) {
            null, "", "auto" -> detectTimeFormat(locale)
            else -> TimeFormat.fromString(v) ?: detectTimeFormat(locale)
        }
        val timestampDisplay = TimestampDisplay.fromString(raw["timestamp_display"])
        val weekStartsOn = when (val v = raw["week_start"]) {
            null, "", "auto" -> detectWeekStart(locale)
            "sunday" -> 0
            "monday" -> 1
            "tuesday" -> 2
            "wednesday" -> 3
            "thursday" -> 4
            "friday" -> 5
            "saturday" -> 6
            else -> 1
        }
        val numberFormat = when (val v = raw["number_format"]) {
            null, "", "auto" -> detectNumberFormat(locale)
            else -> NumberFormat.fromString(v) ?: detectNumberFormat(locale)
        }
        val units = when (val v = raw["units"]) {
            null, "", "auto" -> detectUnits(locale)
            else -> Units.fromString(v)
        }
        val timezone = when (val v = raw["timezone"]) {
            null, "", "auto" -> java.util.TimeZone.getDefault().id
            else -> v
        }
        return UserPreferences(
            dateFormat = dateFormat,
            timeFormat = timeFormat,
            timestampDisplay = timestampDisplay,
            weekStartsOn = weekStartsOn,
            numberFormat = numberFormat,
            units = units,
            timezone = timezone,
            appearance = Appearance.fromString(raw["appearance"]),
            density = Density.fromString(raw["density"]),
            radius = Radius.fromString(raw["radius"]),
            font = FontPref.fromString(raw["font"]),
            fontSize = FontSizePref.fromString(raw["font_size"])
        )
    }

    private fun detectDateFormat(locale: Locale): DateFormat {
        // Sniff order from a known date: 2024-01-15
        return try {
            val sample = java.text.DateFormat
                .getDateInstance(java.text.DateFormat.SHORT, locale)
                .format(java.util.Date(1705276800000L))
            when {
                sample.startsWith("2024") -> DateFormat.YYYY_MM_DD
                sample.startsWith("15") && sample.contains(".") -> DateFormat.DD_DOT_MM_YYYY
                sample.startsWith("15") -> DateFormat.DD_SLASH_MM_YYYY
                else -> DateFormat.MM_SLASH_DD_YYYY
            }
        } catch (_: Exception) {
            DateFormat.YYYY_MM_DD
        }
    }

    private fun detectTimeFormat(locale: Locale): TimeFormat {
        return try {
            val pattern = (java.text.DateFormat.getTimeInstance(
                java.text.DateFormat.SHORT, locale
            ) as? java.text.SimpleDateFormat)?.toPattern().orEmpty()
            if (pattern.contains('a') || pattern.contains('h')) TimeFormat.H12 else TimeFormat.H24
        } catch (_: Exception) {
            TimeFormat.H24
        }
    }

    private fun detectWeekStart(locale: Locale): Int {
        return try {
            // WeekFields.firstDayOfWeek → DayOfWeek (1=Monday … 7=Sunday)
            val day: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
            // Map to JS 0=Sunday … 6=Saturday convention used across the codebase
            if (day == DayOfWeek.SUNDAY) 0 else day.value
        } catch (_: Exception) {
            1
        }
    }

    private fun detectNumberFormat(locale: Locale): NumberFormat {
        return try {
            val sample = java.text.NumberFormat.getNumberInstance(locale).format(1234567.89)
            // Find decimal mark
            val hasCommaDec = sample.endsWith(",89") || sample.endsWith(",9")
            if (hasCommaDec) {
                if (sample.contains('.')) return NumberFormat.EUROPEAN_DOT_COMMA
                if (sample.any { it == ' ' || it == ' ' || it == ' ' }) {
                    return NumberFormat.FRENCH_SPACE_COMMA
                }
                return NumberFormat.EUROPEAN_DOT_COMMA
            }
            if (sample.contains('\'')) return NumberFormat.SWISS_APOSTROPHE_DOT
            // Indian grouping: 12,34,567.89 — last group of 3, then groups of 2
            val intPart = sample.substringBefore('.').filter { it.isDigit() || it == ',' }
            val groups = intPart.split(',').filter { it.isNotEmpty() }
            if (groups.size >= 3 &&
                groups.last().length == 3 &&
                groups[groups.size - 2].length == 2
            ) {
                return NumberFormat.INDIAN_LAKH
            }
            NumberFormat.WESTERN_COMMA_DOT
        } catch (_: Exception) {
            NumberFormat.WESTERN_COMMA_DOT
        }
    }

    private fun detectUnits(locale: Locale): Units {
        val region = locale.country.uppercase()
        return when (region) {
            "US" -> Units.USA
            "GB", "MM", "LR" -> Units.IMPERIAL
            else -> Units.METRIC
        }
    }
}
