package com.ssafy.bookglebookgle.di

import com.ssafy.bookglebookgle.repository.AuthRepository
import com.ssafy.bookglebookgle.repository.AuthRepositoryImpl
import com.ssafy.bookglebookgle.repository.ChatRepository
import com.ssafy.bookglebookgle.repository.ChatRepositoryImpl
import com.ssafy.bookglebookgle.repository.GroupRepository
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import com.ssafy.bookglebookgle.repository.LoginRepositoryImpl
import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.repository.PdfGrpcRepository
import com.ssafy.bookglebookgle.repository.PdfGrpcRepositoryImpl
import com.ssafy.bookglebookgle.repository.PdfRepository
import com.ssafy.bookglebookgle.repository.PdfRepositoryImpl
import com.ssafy.bookglebookgle.repository.ProfileRepository
import com.ssafy.bookglebookgle.repository.ProfileRepositoryImpl
import com.ssafy.bookglebookgle.repository.ShelfRepository
import com.ssafy.bookglebookgle.repository.ShelfRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLoginRepository(
        impl: LoginRepositoryImpl
    ): LoginRepository

    @Binds
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    abstract fun bindPdfRepository(
        impl: PdfRepositoryImpl
    ): PdfRepository

    @Binds
    abstract fun bindGroupRepository(
        impl: GroupRepositoryImpl
    ): GroupRepository

    @Binds
    abstract fun bindChatRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    abstract fun bindPdfGrpcRepository(
        impl: PdfGrpcRepositoryImpl
    ): PdfGrpcRepository

    @Binds
    @Singleton
    abstract fun bindFcmRepository(
        impl: com.ssafy.bookglebookgle.repository.fcm.FcmRepositoryImpl
    ): com.ssafy.bookglebookgle.repository.fcm.FcmRepository


    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        impl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindShelfRepositroy(
        impl: ShelfRepositoryImpl
    ): ShelfRepository
}