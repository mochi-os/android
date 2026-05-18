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
import retrofit2.http.Path
import javax.inject.Singleton

// One screen handles privacy / rules / terms; kind is the path parameter.
// Each kind has its own GET data and POST update under settings/-/document/{kind}/.

data class DocumentData(
    @SerializedName("content") val content: String = "",
)

interface DocumentApi {
    @GET("settings/-/document/{kind}/data")
    suspend fun getDocument(@Path("kind") kind: String): Response<DocumentData>

    @FormUrlEncoded
    @POST("settings/-/document/{kind}/update")
    suspend fun updateDocument(
        @Path("kind") kind: String,
        @Field("content") content: String,
    ): Response<Unit>
}

@Module
@InstallIn(SingletonComponent::class)
object DocumentApiModule {
    @Provides
    @Singleton
    fun provideDocumentApi(@SettingsRetrofit retrofit: Retrofit): DocumentApi =
        retrofit.create(DocumentApi::class.java)
}
