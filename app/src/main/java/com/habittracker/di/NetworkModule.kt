package com.habittracker.di

import android.content.Context
import com.habittracker.data.remote.api.AuthApi
import com.habittracker.data.remote.auth.AuthInterceptor
import com.habittracker.data.remote.auth.DefaultTokenStorage
import com.habittracker.data.remote.auth.TokenStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return loggingInterceptor
    }

    @Provides
    @Singleton
    fun provideTokenStorage(@ApplicationContext context: Context): TokenStorage =
        DefaultTokenStorage(context)

    @Provides
    @Singleton
    @Named("BaseOkHttp")
    fun provideBaseOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

    @Provides
    @Singleton
    @Named("UnauthRetrofit")
    fun provideUnauthRetrofit(@Named("BaseOkHttp") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.habittracker.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(@Named("UnauthRetrofit") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenStorage: TokenStorage,
        authApi: AuthApi
    ): AuthInterceptor = AuthInterceptor(tokenStorage, authApi)

    @Provides
    @Singleton
    @Named("AuthedOkHttp")
    fun provideAuthedOkHttpClient(
        @Named("BaseOkHttp") base: OkHttpClient,
        authInterceptor: AuthInterceptor
    ): OkHttpClient =
        base.newBuilder()
            .addInterceptor(authInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(@Named("AuthedOkHttp") okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.habittracker.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    // TODO: Provide other API services using the authed Retrofit
}


