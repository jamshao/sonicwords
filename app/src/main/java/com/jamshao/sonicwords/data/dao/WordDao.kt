package com.jamshao.sonicwords.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.jamshao.sonicwords.data.entity.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Dao
interface WordDao {
    @Query("SELECT * FROM words")
    fun getAllWordsFlow(): Flow<List<Word>>
    
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Long): Word?
    
    @Query("SELECT * FROM words WHERE word LIKE :wordText LIMIT 1")
    suspend fun findWordByText(wordText: String): Word?
    
    @Query("SELECT * FROM words WHERE word = :text")
    suspend fun getWordByText(text: String): Word?
    
    @Query("SELECT * FROM words WHERE `group` = :groupName OR (`group` IS NULL AND :groupName = 'default')")
    fun getWordsByGroupAsFlow(groupName: String): Flow<List<Word>>
    
    @Query("SELECT * FROM words WHERE `group` = :groupName OR (`group` IS NULL AND :groupName = 'default')")
    suspend fun getWordsByGroup(groupName: String): List<Word>
    
    @Query("SELECT * FROM words WHERE word = :word LIMIT 1")
    suspend fun getWordByWord(word: String): Word?
    
    @Query("SELECT * FROM words WHERE familiarity <= :familiarity")
    fun getWordsByFamiliarity(familiarity: Float): Flow<List<Word>>
    
    @Query("SELECT * FROM words WHERE lastReviewTime <= :reviewTime")
    fun getWordsByReviewTime(reviewTime: Long): Flow<List<Word>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<Word>)
    
    @Update
    suspend fun updateWord(word: Word)
    
    @Delete
    suspend fun deleteWord(word: Word)
    
    @Query("DELETE FROM words WHERE id IN (:ids)")
    suspend fun deleteWordsByIds(ids: List<Long>)
    
    @Query("SELECT * FROM words WHERE isLearned = 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomUnlearnedWords(limit: Int): List<Word>
    
    @Query("SELECT * FROM words WHERE familiarity < 3 ORDER BY familiarity ASC LIMIT :limit")
    suspend fun getWordsForReview(limit: Int): List<Word>
    
    @Query("DELETE FROM words WHERE word = :text")
    suspend fun deleteWordByText(text: String)
    
    @Query("DELETE FROM words")
    suspend fun deleteAllWords()
}