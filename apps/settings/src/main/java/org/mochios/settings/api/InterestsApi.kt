package org.mochios.settings.api

import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mochios.settings.ui.profile.SettingsRetrofit
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import javax.inject.Singleton

// Mirrors apps/settings/web/src/hooks/use-interests.ts and apps/settings/user/interests.star.
// Endpoints live under `settings/-/user/interests`; the canonical identifier
// is the Wikidata QID (no separate row id), and there is a single set/remove
// pair plus a search-by-query POST and a summary regenerate POST.

data class Interest(
    @SerializedName("qid") val qid: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("weight") val weight: Int = 0,
    @SerializedName("updated") val updated: Long = 0,
)

data class InterestsData(
    @SerializedName("interests") val interests: List<Interest> = emptyList(),
    @SerializedName("summary") val summary: String = "",
)

data class InterestSearchResult(
    @SerializedName("qid") val qid: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("description") val description: String = "",
)

data class InterestSearchResponse(
    @SerializedName("results") val results: List<InterestSearchResult> = emptyList(),
)

data class InterestSummaryResponse(
    @SerializedName("summary") val summary: String = "",
)

interface InterestsApi {
    @GET("settings/-/user/interests")
    suspend fun getInterests(): Response<InterestsData>

    @FormUrlEncoded
    @POST("settings/-/user/interests/set")
    suspend fun setInterest(
        @Field("qid") qid: String,
        @Field("weight") weight: Int,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/interests/remove")
    suspend fun removeInterest(@Field("qid") qid: String): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/user/interests/search")
    suspend fun searchInterests(@Field("query") query: String): Response<InterestSearchResponse>

    @POST("settings/-/user/interests/summary")
    suspend fun regenerateSummary(): Response<InterestSummaryResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object InterestsApiModule {
    @Provides
    @Singleton
    fun provideInterestsApi(@SettingsRetrofit retrofit: Retrofit): InterestsApi =
        retrofit.create(InterestsApi::class.java)
}
