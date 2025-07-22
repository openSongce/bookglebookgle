package com.ssafy.bookglebookgle.di

import com.ssafy.bookglebookgle.network.AuthInterceptor
import com.ssafy.bookglebookgle.network.LoginApi
import com.ssafy.bookglebookgle.network.TokenAuthenticator
import com.ssafy.bookglebookgle.util.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://your.api.url/" // 실제 서버 주소로 교체

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenManager: TokenManager
    ): AuthInterceptor = AuthInterceptor(tokenManager)

    @Provides
    @Singleton
    fun provideLoginApi(
        retrofit: Retrofit
    ): LoginApi = retrofit.create(LoginApi::class.java)

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenManager: TokenManager,
        loginApi: Provider<LoginApi>
    ): TokenAuthenticator = TokenAuthenticator(tokenManager, loginApi)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)      // AccessToken 자동 첨부
        .authenticator(tokenAuthenticator)    // RefreshToken 자동 갱신
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

}
