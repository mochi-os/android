// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.notifications

import org.mochios.android.api.ApiResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NotificationsApi {

    @GET("-/list")
    suspend fun list(): Response<NotificationsListResponse>

    @GET("-/count")
    suspend fun count(): Response<ApiResponse<NotificationsCount>>

    @FormUrlEncoded
    @POST("-/read")
    suspend fun read(@Field("id") id: String): Response<ApiResponse<Map<String, Any>>>

    @POST("-/read/all")
    suspend fun readAll(): Response<ApiResponse<Map<String, Any>>>

    @POST("-/clear/all")
    suspend fun clearAll(): Response<ApiResponse<Map<String, Any>>>

    @FormUrlEncoded
    @POST("-/accounts/remove")
    suspend fun removeAccount(
        @Field("id") id: String,
    ): Response<ApiResponse<Map<String, Any>>>

    @GET("-/categories/list")
    suspend fun categories(): Response<ApiResponse<List<NotificationCategory>>>

    /** The topic row for one notification's app, topic and object; null data when none exists yet. */
    @GET("-/topics/lookup")
    suspend fun topic(
        @Query("app") app: String,
        @Query("topic") topic: String,
        @Query("object") target: String,
    ): Response<ApiResponse<NotificationTopic?>>

    /** Moves a topic to [category]; an empty one clears it to unassigned. */
    @FormUrlEncoded
    @POST("-/topics/category/set")
    suspend fun setCategory(
        @Field("app") app: String,
        @Field("topic") topic: String,
        @Field("object") target: String,
        @Field("category") category: String,
    ): Response<ApiResponse<Map<String, Any>>>

    @GET("-/rss/list")
    suspend fun rssFeeds(): Response<ApiResponse<List<RssFeed>>>

    /** [existing] "1" adds the feed to every category the user has now. */
    @FormUrlEncoded
    @POST("-/rss/create")
    suspend fun createRssFeed(
        @Field("name") name: String,
        @Field("existing") existing: String,
    ): Response<ApiResponse<RssFeed>>

    @FormUrlEncoded
    @POST("-/rss/rename")
    suspend fun renameRssFeed(
        @Field("id") id: String,
        @Field("name") name: String,
    ): Response<ApiResponse<Map<String, Any>>>

    /** [enabled] "1" or "0": whether the feed joins each new category. */
    @FormUrlEncoded
    @POST("-/rss/update")
    suspend fun enableRssFeed(
        @Field("id") id: String,
        @Field("enabled") enabled: String,
    ): Response<ApiResponse<Map<String, Any>>>

    @FormUrlEncoded
    @POST("-/rss/delete")
    suspend fun deleteRssFeed(@Field("id") id: String): Response<ApiResponse<Map<String, Any>>>
}
