package com.ssafy.bookglebookgle.pdf.di

import android.content.Context
import com.ssafy.bookglebookgle.pdf.room.AppDatabase
import com.ssafy.bookglebookgle.pdf.room.Dao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideDao(database: AppDatabase): Dao {
        return database.getDao()
    }
}