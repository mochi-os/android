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

// Mirrors apps/settings/web/src/features/user/notifications.tsx. The web app calls
// the settings app's `-/notifications/{categories,topics,destinations}` endpoints
// via apiClient; Android hits the same endpoints through the settings retrofit.

data class DestinationRow(
    val type: String = "",
    val target: String = "",
)

data class NotifCategory(
    val id: Int = 0,
    val label: String = "",
    val default: Int = 0,
    val created: Long = 0,
    val destinations: List<DestinationRow> = emptyList(),
)

data class DestinationAccount(
    val id: Int = 0,
    val type: String = "",
    val label: String = "",
    val identifier: String = "",
    val enabled: Int = 0,
)

data class DestinationFeed(
    val id: String = "",
    val name: String = "",
    val enabled: Int = 0,
)

data class DestinationsAvailable(
    val accounts: List<DestinationAccount> = emptyList(),
    val feeds: List<DestinationFeed> = emptyList(),
)

data class NotifTopic(
    val id: Int = 0,
    val app: String = "",
    @SerializedName("app_name") val appName: String = "",
    val topic: String = "",
    val `object`: String = "",
    @SerializedName("object_name") val objectName: String = "",
    val label: String = "",
    val category: Int? = null,
    val created: Long = 0,
)

// Action responses are wrapped in `{data: ...}` by the settings app.
data class CategoriesEnvelope(val data: List<NotifCategory> = emptyList())
data class TopicsEnvelope(val data: List<NotifTopic> = emptyList())
data class DestinationsEnvelope(val data: DestinationsAvailable = DestinationsAvailable())
data class TestResult(val sent: Int = 0, val web: Boolean = false)
data class TestEnvelope(val data: TestResult = TestResult())

interface NotificationPrefsApi {
    @GET("settings/-/notifications/categories")
    suspend fun getCategories(): Response<CategoriesEnvelope>

    @GET("settings/-/notifications/topics")
    suspend fun getTopics(): Response<TopicsEnvelope>

    @GET("settings/-/notifications/destinations")
    suspend fun getDestinations(): Response<DestinationsEnvelope>

    @FormUrlEncoded
    @POST("settings/-/notifications/categories/create")
    suspend fun createCategory(
        @Field("label") label: String,
        @Field("destinations") destinations: String? = null,
        @Field("default") default: String? = null,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/notifications/categories/update")
    suspend fun updateCategory(
        @Field("id") id: Int,
        @Field("label") label: String? = null,
        @Field("destinations") destinations: String? = null,
        @Field("default") default: String? = null,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/notifications/categories/delete")
    suspend fun deleteCategory(
        @Field("id") id: Int,
        @Field("reassign_to") reassignTo: Int,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/notifications/categories/test")
    suspend fun testCategory(@Field("id") id: Int): Response<TestEnvelope>

    @FormUrlEncoded
    @POST("settings/-/notifications/topics/set_category")
    suspend fun setTopicCategory(
        @Field("id") id: Int,
        @Field("category") category: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("settings/-/notifications/topics/delete")
    suspend fun deleteTopic(@Field("id") id: Int): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object NotificationPrefsApiModule {
    @Provides
    @Singleton
    fun provideNotificationPrefsApi(@SettingsRetrofit retrofit: Retrofit): NotificationPrefsApi =
        retrofit.create(NotificationPrefsApi::class.java)
}
