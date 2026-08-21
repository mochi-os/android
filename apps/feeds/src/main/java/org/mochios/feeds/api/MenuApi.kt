// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.api

import org.mochios.android.api.ApiResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/** Response from `menu/-/permissions/name`: the human label for a permission key. */
data class PermissionNameResponse(val name: String = "")

/** Response from `menu/-/permissions/grant`: the resulting grant status. */
data class PermissionGrantResponse(val status: String = "")

/**
 * The shell's `menu/` permission endpoints, for the `permission_required` flow
 * from `sources/add`; authorised by the session cookie.
 */
interface MenuApi {

    @FormUrlEncoded
    @POST("-/permissions/name")
    suspend fun permissionName(
        @Field("permission") permission: String
    ): Response<ApiResponse<PermissionNameResponse>>

    @FormUrlEncoded
    @POST("-/permissions/grant")
    suspend fun grantPermission(
        @Field("app") app: String,
        @Field("permission") permission: String
    ): Response<ApiResponse<PermissionGrantResponse>>
}
