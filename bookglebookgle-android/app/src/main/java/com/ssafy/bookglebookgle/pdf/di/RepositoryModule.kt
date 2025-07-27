package com.ssafy.bookglebookgle.pdf.di

import com.ssafy.bookglebookgle.pdf.repository.PDFRepository
import com.ssafy.bookglebookgle.pdf.repository.PdfRepositoryImpl
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