// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

internal data class FullPrefsResponse(
    val preferences: Map<String, String>?,
    val themes: List<RawTheme>?,
    val default_theme: String?,
)

internal data class RawTheme(
    val id: String?,
    val hue: Double?,
    val chroma: Double?,
    val hue_bg: Double?,
)

internal interface PreferencesApi {
    @GET("settings/-/user/preferences/data")
    suspend fun getPreferences(@Header("Authorization") token: String): Response<FullPrefsResponse>

    @retrofit2.http.FormUrlEncoded
    @POST("settings/-/user/preferences/set")
    suspend fun setPreferences(
        @Header("Authorization") token: String,
        @retrofit2.http.FieldMap fields: Map<String, String>,
    ): Response<Map<String, Any>>

    @POST("settings/-/user/preferences/reset")
    suspend fun resetPreferences(
        @Header("Authorization") token: String,
    ): Response<Map<String, Any>>
}
