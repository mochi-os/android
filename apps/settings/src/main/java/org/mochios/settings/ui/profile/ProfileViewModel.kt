package org.mochios.settings.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

data class Identity(
    val entity: String = "",
    val fingerprint: String = "",
    val username: String = "",
    val name: String = "",
    val privacy: String = "private",
)

data class IdentityResponse(@SerializedName("entity") val entity: String = "",
                            @SerializedName("fingerprint") val fingerprint: String = "",
                            @SerializedName("username") val username: String = "",
                            @SerializedName("name") val name: String = "",
                            @SerializedName("privacy") val privacy: String = "private")

interface ProfileApi {
    @GET("settings/-/user/account/identity")
    suspend fun getIdentity(): Response<IdentityResponse>

    @FormUrlEncoded
    @POST("settings/-/user/account/identity/update")
    suspend fun updateIdentity(
        @Field("name") name: String? = null,
        @Field("privacy") privacy: String? = null,
    ): Response<Map<String, Any>>
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsRetrofit

@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {
    @Provides
    @Singleton
    @SettingsRetrofit
    fun provideSettingsRetrofit(
        okHttpClient: OkHttpClient,
        gson: com.google.gson.Gson,
        sessionManager: SessionManager,
    ): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
        val client = okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val token = sessionManager.getTokenBlocking("settings")
                val req = chain.request().newBuilder()
                    .header("Accept", "application/json")
                if (token != null) req.header("Authorization", "Bearer $token")
                chain.proceed(req.build())
            })
            .build()
        return Retrofit.Builder()
            .baseUrl("$serverUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideProfileApi(@SettingsRetrofit retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val identity: Identity = Identity(),
    val nameDraft: String = "",
    val error: MochiError? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: ProfileApi,
    sessionManager: SessionManager,
) : ViewModel() {

    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val resp = api.getIdentity()
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
                val body = resp.body() ?: throw RuntimeException("empty body")
                val id = Identity(
                    entity = body.entity,
                    fingerprint = body.fingerprint,
                    username = body.username,
                    name = body.name,
                    privacy = body.privacy,
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    identity = id,
                    nameDraft = id.name,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun updateName(draft: String) {
        _uiState.value = _uiState.value.copy(nameDraft = draft)
    }

    fun saveName() {
        val name = _uiState.value.nameDraft.trim()
        if (name.isBlank() || name == _uiState.value.identity.name) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val resp = api.updateIdentity(name = name)
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    identity = _uiState.value.identity.copy(name = name),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    fun setPrivacy(privacy: String) {
        if (privacy != "public" && privacy != "private") return
        if (privacy == _uiState.value.identity.privacy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val resp = api.updateIdentity(privacy = privacy)
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    identity = _uiState.value.identity.copy(privacy = privacy),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }
}
