// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET

/**
 * One app on the home screen as the server lists it. [link] is the path the
 * app is served at, which is also the name the client knows it by; [name] is
 * its label in the user's language; [highlight] asks for attention, as Help
 * does until it is first opened.
 */
data class Icon(
    val id: String = "",
    val path: String = "",
    val name: String = "",
    val file: String = "",
    val link: String = "",
    val highlight: Boolean = false,
)

/**
 * The home app's `-/icons` answer, sent as is rather than wrapped in `data`.
 * [mask] and [background] come from the user's theme: the shape of the tile
 * each icon is drawn on and its colour, or null for the plain look.
 */
data class IconsResponse(
    val icons: List<Icon> = emptyList(),
    @SerializedName("icon_mask") val mask: String? = null,
    @SerializedName("icon_background") val background: String? = null,
)

/** The home app's actions, at the server's root, where the home app is served. */
interface HomeApi {

    /** The apps the user can open, after the server's role and visibility checks. */
    @GET("-/icons")
    suspend fun icons(): Response<IconsResponse>
}
