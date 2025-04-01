package com.jamshao.sonicwords.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jamshao.sonicwords.data.dao.LearningStatisticsDao
import com.jamshao.sonicwords.data.dao.WordDao
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.model.LearningStatistics

@Database(entities = [Word::class, LearningStatistics::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun learningStatisticsDao(): LearningStatisticsDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // 数据库迁移：从版本1到版本2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 添加 Word 表中缺少的字段
                database.execSQL("ALTER TABLE words ADD COLUMN correctCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE words ADD COLUMN familiarity INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE words ADD COLUMN nextReviewTime INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE words ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0")
                
                // 2. 创建学习统计表
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `learning_statistics` " +
                    "(`date` TEXT NOT NULL, " +
                    "`learnedCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`reviewCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`correctCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`errorCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`studyTimeMillis` INTEGER NOT NULL DEFAULT 0, " +
                    "`masteredCount` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`date`))"
                )
            }
        }

        // 数据库迁移：从版本2到版本3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加 group 字段
                database.execSQL("ALTER TABLE words ADD COLUMN `group` TEXT DEFAULT NULL")
            }
        }
        
        // 数据库迁移：从版本3到版本4
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 创建临时表，包含所有需要的字段，使用正确的数据类型
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `words_temp` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`word` TEXT NOT NULL, " +
                    "`meaning` TEXT NOT NULL, " +
                    "`chineseMeaning` TEXT, " +
                    "`familiarity` REAL NOT NULL DEFAULT 0, " +
                    "`errorCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`isLearned` INTEGER NOT NULL DEFAULT 0, " +
                    "`lastStudyTime` INTEGER, " +
                    "`lastReviewTime` INTEGER, " +
                    "`group` TEXT DEFAULT NULL)"
                )
                
                // 2. 从旧表复制数据到临时表，将 familiarity 从 INTEGER 转换为 REAL
                database.execSQL(
                    "INSERT INTO words_temp (id, word, meaning, errorCount, isLearned, lastReviewTime, familiarity, `group`) " +
                    "SELECT id, word, meaning, errorCount, isLearned, lastReviewTime, CAST(familiarity AS REAL), `group` FROM words"
                )
                
                // 3. 删除旧表
                database.execSQL("DROP TABLE words")
                
                // 4. 将临时表重命名为正式表
                database.execSQL("ALTER TABLE words_temp RENAME TO words")
            }
        }

        // 数据库迁移：从版本4到版本5
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建一个完全符合当前实体定义的新表
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `words_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`word` TEXT NOT NULL, " +
                    "`meaning` TEXT NOT NULL, " +
                    "`chineseMeaning` TEXT, " +
                    "`familiarity` REAL NOT NULL DEFAULT 0, " +
                    "`errorCount` INTEGER NOT NULL DEFAULT 0, " +
                    "`isLearned` INTEGER NOT NULL DEFAULT 0, " +
                    "`lastStudyTime` INTEGER, " +
                    "`lastReviewTime` INTEGER, " +
                    "`group` TEXT DEFAULT 'default')"
                )
                
                // 尝试从现有表复制所有可能的数据
                try {
                    // 获取现有表的列信息
                    val cursor = database.query("PRAGMA table_info(words)")
                    val columns = mutableListOf<String>()
                    while (cursor.moveToNext()) {
                        columns.add(cursor.getString(cursor.getColumnIndex("name")))
                    }
                    cursor.close()
                    
                    // 构建复制语句，只复制存在的列
                    val sourceColumns = mutableListOf<String>()
                    val targetColumns = mutableListOf<String>()
                    
                    // 必须存在的基本列
                    if (columns.contains("id")) {
                        sourceColumns.add("id")
                        targetColumns.add("id")
                    }
                    if (columns.contains("word")) {
                        sourceColumns.add("word")
                        targetColumns.add("word")
                    }
                    if (columns.contains("meaning")) {
                        sourceColumns.add("meaning")
                        targetColumns.add("meaning")
                    }
                    
                    // 可能存在的其他列
                    if (columns.contains("chineseMeaning")) {
                        sourceColumns.add("chineseMeaning")
                        targetColumns.add("chineseMeaning")
                    }
                    if (columns.contains("familiarity")) {
                        sourceColumns.add("familiarity")
                        targetColumns.add("familiarity")
                    }
                    if (columns.contains("errorCount")) {
                        sourceColumns.add("errorCount")
                        targetColumns.add("errorCount")
                    }
                    if (columns.contains("isLearned")) {
                        sourceColumns.add("isLearned")
                        targetColumns.add("isLearned")
                    }
                    if (columns.contains("lastStudyTime")) {
                        sourceColumns.add("lastStudyTime")
                        targetColumns.add("lastStudyTime")
                    }
                    if (columns.contains("lastReviewTime")) {
                        sourceColumns.add("lastReviewTime")
                        targetColumns.add("lastReviewTime")
                    }
                    if (columns.contains("group")) {
                        sourceColumns.add("`group`")
                        targetColumns.add("`group`")
                    }
                    
                    // 构建并执行复制语句
                    val sourceColumnsStr = sourceColumns.joinToString(", ")
                    val targetColumnsStr = targetColumns.joinToString(", ")
                    
                    if (sourceColumns.isNotEmpty()) {
                        database.execSQL(
                            "INSERT INTO words_new ($targetColumnsStr) " +
                            "SELECT $sourceColumnsStr FROM words"
                        )
                    }
                } catch (e: Exception) {
                    // 如果出现异常，尝试基本复制
                    database.execSQL(
                        "INSERT INTO words_new (id, word, meaning) " +
                        "SELECT id, word, meaning FROM words"
                    )
                }
                
                // 删除旧表并重命名新表
                database.execSQL("DROP TABLE words")
                database.execSQL("ALTER TABLE words_new RENAME TO words")
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sonicwords_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5) // 添加迁移策略
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}