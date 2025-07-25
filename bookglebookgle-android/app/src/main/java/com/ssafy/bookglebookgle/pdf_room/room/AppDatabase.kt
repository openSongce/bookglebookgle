package com.ssafy.bookglebookgle.pdf_room.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ssafy.bookglebookgle.pdf_room.room.entity.BookmarkEntity
import com.ssafy.bookglebookgle.pdf_room.room.entity.CommentEntity
import com.ssafy.bookglebookgle.pdf_room.room.entity.HighlightEntity
import com.ssafy.bookglebookgle.pdf_room.room.entity.PdfNoteEntity
import com.ssafy.bookglebookgle.pdf_room.room.entity.PdfTagEntity

// Todo: 외부 서버 API Retrofit 연동 변경

@Database(
    entities = [
        PdfNoteEntity::class,
        PdfTagEntity::class,
        CommentEntity::class,
        HighlightEntity::class,
        BookmarkEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = false
)

@TypeConverters(TypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun getDao(): Dao

    companion object {
        private const val DATABASE_NAME = "bookgle_database"
        const val VERSION = 2

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}