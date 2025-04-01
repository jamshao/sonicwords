package com.jamshao.sonicwords.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
// import java.time.Instant // 不再需要 Instant

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val meaning: String,
    val chineseMeaning: String? = null,
    val familiarity: Float = 0f,
    val errorCount: Int = 0,
    val isLearned: Boolean = false,
    val lastStudyTime: Long? = null,
    val lastReviewTime: Long? = null,
    val group: String? = "default"
)