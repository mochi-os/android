package org.mochios.android.i18n

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    internal fun providePreferencesApi(retrofit: Retrofit): PreferencesApi {
        return retrofit.create(PreferencesApi::class.java)
    }
}
