package org.mochi.android.places

import com.google.gson.JsonParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.mochi.android.model.PlaceData
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Lightweight client for the Nominatim geocoding API. Used by PlacePicker
 * for autocomplete search and reverse geocoding. Has its own OkHttpClient
 * so the Authorization/cookie interceptors used by the Mochi API don't
 * leak to OpenStreetMap.
 */

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NominatimClient

@Module
@InstallIn(SingletonComponent::class)
object NominatimModule {
    @Provides
    @Singleton
    @NominatimClient
    fun provideNominatimClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
}

data class NominatimPlace(
    val name: String,
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val osmId: String
) {
    fun toPlaceData(): PlaceData = PlaceData(
        name = displayName,
        lat = lat,
        lon = lon,
        category = category
    )
}

@Singleton
class NominatimService @Inject constructor(
    @NominatimClient private val client: OkHttpClient
) {
    companion object {
        private const val BASE = "https://nominatim.openstreetmap.org"
        // Nominatim's TOS requires a descriptive User-Agent identifying the app.
        private const val USER_AGENT = "org.mochi.android/1.0"
    }

    suspend fun search(query: String, limit: Int = 8): List<NominatimPlace> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return withContext(Dispatchers.IO) {
            val url = "$BASE/search".toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("addressdetails", "1")
                .addQueryParameter("q", trimmed)
                .addQueryParameter("limit", limit.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<NominatimPlace>()
                val body = response.body?.string() ?: return@use emptyList<NominatimPlace>()
                val arr = runCatching { JsonParser.parseString(body).asJsonArray }
                    .getOrNull() ?: return@use emptyList<NominatimPlace>()
                arr.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val display = obj.get("display_name")?.asString ?: return@mapNotNull null
                    val lat = obj.get("lat")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = obj.get("lon")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                    val category = obj.get("class")?.asString ?: ""
                    val osmType = obj.get("osm_type")?.asString ?: ""
                    val osmId = obj.get("osm_id")?.asString ?: ""
                    val short = obj.get("name")?.asString
                        ?: display.substringBefore(",").trim()
                    NominatimPlace(
                        name = short,
                        displayName = display,
                        lat = lat,
                        lon = lon,
                        category = category,
                        osmId = "$osmType$osmId"
                    )
                }
            }
        }
    }

    suspend fun reverse(lat: Double, lon: Double): NominatimPlace? {
        return withContext(Dispatchers.IO) {
            val url = "$BASE/reverse".toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("addressdetails", "1")
                .addQueryParameter("lat", lat.toString())
                .addQueryParameter("lon", lon.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val obj = runCatching { JsonParser.parseString(body).asJsonObject }
                    .getOrNull() ?: return@use null
                val display = obj.get("display_name")?.asString ?: return@use null
                val rLat = obj.get("lat")?.asString?.toDoubleOrNull() ?: lat
                val rLon = obj.get("lon")?.asString?.toDoubleOrNull() ?: lon
                val category = obj.get("class")?.asString ?: ""
                val osmType = obj.get("osm_type")?.asString ?: ""
                val osmId = obj.get("osm_id")?.asString ?: ""
                val short = obj.get("name")?.asString
                    ?: display.substringBefore(",").trim()
                NominatimPlace(
                    name = short,
                    displayName = display,
                    lat = rLat,
                    lon = rLon,
                    category = category,
                    osmId = "$osmType$osmId"
                )
            }
        }
    }
}
