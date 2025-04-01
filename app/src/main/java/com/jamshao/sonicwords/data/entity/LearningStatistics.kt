package com.jamshao.sonicwords.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_statistics")
data class LearningStatistics(
    @PrimaryKey val date: String,
    val studyTime: Long = 0L,
    val reviewCount: Int = 0,
    val correctRate: Float = 0f,
    val learnedCount: Int = 0,
    val correctCount: Int = 0,
    val errorCount: Int = 0,
    val masteredCount: Int = 0
) 