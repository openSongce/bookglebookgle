package com.ssafy.bookglebookgle.di

import com.ssafy.bookglebookgle.network.api.AuthApi
import com.ssafy.bookglebookgle.network.api.LoginApi
import com.ssafy.bookglebookgle.repository.AuthRepository
import com.ssafy.bookglebookgle.repository.LoginRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideLoginRepository(loginApi: LoginApi): LoginRepository {
        return LoginRepository(loginApi)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(authApi: AuthApi): AuthRepository {
        return AuthRepository(authApi)
    }
}