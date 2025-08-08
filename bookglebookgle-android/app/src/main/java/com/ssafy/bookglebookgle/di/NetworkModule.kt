package com.ssafy.bookglebookgle.di

import com.ssafy.bookglebookgle.repository.ChatGrpcRepository
import com.ssafy.bookglebookgle.network.api.AuthApi
import com.ssafy.bookglebookgle.network.AuthInterceptor
import com.ssafy.bookglebookgle.network.api.LoginApi
import com.ssafy.bookglebookgle.network.TokenAuthenticator
import com.ssafy.bookglebookgle.network.api.ChatApi
import com.ssafy.bookglebookgle.network.api.GroupApi
import com.ssafy.bookglebookgle.network.api.PdfApi
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
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import com.example.bookglebookgleserver.chat.ChatServiceGrpc
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "http://52.79.59.66:8081/" // TODO: 실제 서버 주소로 교체
    private const val GRPC_SERVER_URL = "52.79.59.66"  // gRPC 서버 주소
    private const val GRPC_SERVER_PORT = 6565

    /**
     * API
     * */
    @Provides
    @Singleton
    fun provideLoginApi(
        retrofit: Retrofit
    ): LoginApi = retrofit.create(LoginApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun providePdfApi(retrofit: Retrofit): PdfApi =
        retrofit.create(PdfApi::class.java)

    @Provides
    @Singleton
    fun provideGroupApi(retrofit: Retrofit): GroupApi =
        retrofit.create(GroupApi::class.java)

    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi =
        retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenManager: TokenManager
    ): AuthInterceptor = AuthInterceptor(tokenManager)

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

    /**
     * gRPC 관련
     * */
    @Provides
    @Singleton
    fun provideGrpcChannel(): ManagedChannel {
        return ManagedChannelBuilder.forAddress(GRPC_SERVER_URL, GRPC_SERVER_PORT)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideChatServiceStub(channel: ManagedChannel): ChatServiceGrpc.ChatServiceStub {
        return ChatServiceGrpc.newStub(channel)
    }

    @Provides
    @Singleton
    fun provideChatGrpcRepository(
        channel: ManagedChannel,
        chatStub: ChatServiceGrpc.ChatServiceStub
    ): ChatGrpcRepository {
        return ChatGrpcRepository(channel, chatStub)
    }

    @Provides
    @Singleton
    fun provideFcmApi(retrofit: Retrofit): com.ssafy.bookglebookgle.network.api.FcmApi =
        retrofit.create(com.ssafy.bookglebookgle.network.api.FcmApi::class.java)


}
