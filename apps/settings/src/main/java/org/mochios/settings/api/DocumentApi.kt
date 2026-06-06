package org.mochios.settings.api

import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mochios.settings.api.SettingsRetrofit
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import javax.inject.Singleton

/**
 * READ-ONLY viewer for the three public legal documents (privacy / rules /
 * terms), opened by any signed-in user from the settings home list.
 *
 * This is deliberately NOT the document editor. Operators edit documents —
 * including the per-language dimension this viewer doesn't expose — through
 * [SystemDocumentsApi] / SystemDocumentsScreen, backed by the admin-gated
 * `-/system/document/get` and `-/system/document/set` routes. This screen only
 * reads, via the PUBLIC
 * `-/document/get` action, which resolves the viewer's language server-side and
 * returns the body as markdown plus pre-rendered, sanitised HTML.
 *
 * Mirrors web's shared `DocumentPage` (`@mochi/web`), which POSTs the same
 * endpoint with `{ name }` and reads `{ name, body, html }`.
 *
 * (Historical note: this previously POSTed to `-/document/{kind}/data` and
 * `-/document/{kind}/update` — routes that never existed server-side — and was
 * built as an editor. It could not have worked; it is now the viewer it was
 * always meant to be.)
 */
data class DocumentData(
    @SerializedName("name") val name: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("html") val html: String = "",
)

interface DocumentApi {
    /** [name] is "privacy", "rules", or "terms". */
    @FormUrlEncoded
    @POST("settings/-/document/get")
    suspend fun getDocument(@Field("name") name: String): Response<DocumentData>
}

@Module
@InstallIn(SingletonComponent::class)
object DocumentApiModule {
    @Provides
    @Singleton
    fun provideDocumentApi(@SettingsRetrofit retrofit: Retrofit): DocumentApi =
        retrofit.create(DocumentApi::class.java)
}
