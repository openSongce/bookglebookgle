package com.ssafy.bookglebookgle.pdf_room.di

import com.ssafy.bookglebookgle.pdf_room.repository.PDFRepository
import com.ssafy.bookglebookgle.pdf_room.repository.PdfRepositoryImpl
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
    abstract fun bindPdfRepository(impl: PdfRepositoryImpl): PDFRepository
}