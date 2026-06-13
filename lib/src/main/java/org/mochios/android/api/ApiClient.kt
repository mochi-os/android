package org.mochios.android.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.mochios.android.auth.SessionManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppContext

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InvalidationInterceptor

@Module
@InstallIn(SingletonComponent::class)
object ApiClient {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        // Mochi's SQLite layer stores booleans as 0/1 integers, which surface
        // unchanged in JSON responses. Tolerate that on the client so Kotlin
        // `Boolean` fields parse cleanly.
        val boolAdapter = object : TypeAdapter<Boolean>() {
            override fun read(reader: JsonReader): Boolean = when (reader.peek()) {
                JsonToken.NUMBER -> reader.nextInt() != 0
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.STRING -> reader.nextString().let { it == "true" || it == "1" }
                JsonToken.NULL -> { reader.nextNull(); false }
                else -> { reader.skipValue(); false }
            }
            override fun write(writer: JsonWriter, value: Boolean?) {
                if (value == null) writer.nullValue() else writer.value(value)
            }
        }
        return GsonBuilder()
            .setLenient()
            .registerTypeAdapter(Boolean::class.java, boolAdapter)
            .registerTypeAdapter(Boolean::class.javaPrimitiveType, boolAdapter)
            .create()
    }

    @Provides
    @Singleton
    @AuthInterceptor
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val appName = original.header("X-Mochi-App")
            val builder = original.newBuilder()

            // Ask the server for JSON errors. The Mochi server content-negotiates
            // (Action.error in core/server/actions.go): without this header it
            // serves an HTML error page, which the client then rendered as raw
            // markup — the "ugly web-page error". With it, errors come back as
            // structured {error, message} JSON with a localised message.
            if (original.header("Accept") == null) {
                builder.header("Accept", "application/json")
            }

            if (appName != null) {
                builder.removeHeader("X-Mochi-App")
                val token = sessionManager.getTokenBlocking(appName)
                if (token != null) {
                    builder.header("Authorization", "Bearer $token")
                }
            }

            builder.build().let { chain.proceed(it) }
        }
    }

    /**
     * Watches every response for 401 (authentication failed). When seen we
     * tear down the local session and the matching AccountManager record so
     * the app falls back to the login screen — instead of looping forever
     * with a dead cookie. 403 is *not* treated as session-dead: it can mean
     * "authenticated but missing app token" (which the per-app JWT
     * interceptors handle) or "authenticated but no permission" (legitimate).
     */
    @Provides
    @Singleton
    @InvalidationInterceptor
    fun provideInvalidationInterceptor(sessionManager: SessionManager): Interceptor {
        return Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                kotlinx.coroutines.runBlocking { sessionManager.clearAll() }
            }
            response
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @AuthInterceptor authInterceptor: Interceptor,
        @InvalidationInterceptor invalidationInterceptor: Interceptor,
        sessionManager: SessionManager
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(invalidationInterceptor)
            .cookieJar(sessionManager.cookieJar)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        builder.addInterceptor(loggingInterceptor)

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson, sessionManager: SessionManager): Retrofit {
        val serverUrl = sessionManager.getServerUrlBlocking()
        return createRetrofit(serverUrl, okHttpClient, gson)
    }

    fun createRetrofit(serverUrl: String, okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
