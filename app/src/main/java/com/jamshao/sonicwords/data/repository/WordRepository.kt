package com.jamshao.sonicwords.data.repository

import com.jamshao.sonicwords.data.dao.WordDao
import com.jamshao.sonicwords.data.entity.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordRepository @Inject constructor(
    private val wordDao: WordDao,
    private val applicationScope: CoroutineScope = CoroutineScope(Dispatchers.IO) // 注入或创建一个应用级别的Scope
) {
    val allWords: StateFlow<List<Word>> = wordDao.getAllWordsFlow()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(5000), // 5秒后停止共享
            initialValue = emptyList()
        )
    
    fun getWordsByFamiliarity(familiarity: Float): Flow<List<Word>> = 
        wordDao.getWordsByFamiliarity(familiarity)
    
    fun getWordsByReviewTime(reviewTime: Long): Flow<List<Word>> = 
        wordDao.getWordsByReviewTime(reviewTime)
    
    suspend fun insertWord(word: Word) = wordDao.insertWord(word)
    
    suspend fun insertWords(words: List<Word>) = wordDao.insertWords(words)
    
    suspend fun updateWord(word: Word) = wordDao.updateWord(word)
    
    suspend fun deleteWord(word: Word) = wordDao.deleteWord(word)
    
    suspend fun getWordByText(word: String): Word? = wordDao.getWordByWord(word)
    
    fun getWordsByGroup(groupName: String): StateFlow<List<Word>> = wordDao.getWordsByGroupAsFlow(groupName)
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun deleteAllWords() = wordDao.deleteAllWords()
}