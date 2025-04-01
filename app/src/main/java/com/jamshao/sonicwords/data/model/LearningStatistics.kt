package com.jamshao.sonicwords.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 学习统计数据模型
 * 用于存储每日学习统计数据
 */
@Entity(tableName = "learning_statistics")
data class LearningStatistics(
    @PrimaryKey val date: String,
    val learnedCount: Int = 0,
    val reviewCount: Int = 0,
    val correctCount: Int = 0,
    val errorCount: Int = 0,
    val studyTimeMillis: Long = 0,
    val masteredCount: Int = 0
) {
    constructor(
        dateTimestamp: Long,
        learnedCount: Int = 0,
        reviewCount: Int = 0,
        correctCount: Int = 0, 
        errorCount: Int = 0,
        studyTimeMillis: Long = 0,
        masteredCount: Int = 0
    ) : this(
        date = LocalDate.ofInstant(
            Instant.ofEpochMilli(dateTimestamp), 
            ZoneId.systemDefault()
        ).toString(),
        learnedCount = learnedCount,
        reviewCount = reviewCount,
        correctCount = correctCount,
        errorCount = errorCount,
        studyTimeMillis = studyTimeMillis,
        masteredCount = masteredCount
    )
    
    companion object {
        /**
         * 创建今日的学习统计数据
         */
        fun createToday(): LearningStatistics {
            return LearningStatistics(date = LocalDate.now().toString())
        }
    }
}
