package com.jamshao.sonicwords.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jamshao.sonicwords.data.dao.WordDao
import com.jamshao.sonicwords.data.entity.Word

@Database(entities = [Word::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
} 