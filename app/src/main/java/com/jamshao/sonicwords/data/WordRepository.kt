package com.jamshao.sonicwords.data

import com.jamshao.sonicwords.data.dao.LearningStatisticsDao
import com.jamshao.sonicwords.data.dao.WordDao
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.model.LearningStatistics
import com.jamshao.sonicwords.service.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordRepository @Inject constructor(
    private val wordDao: WordDao,
    private val learningStatisticsDao: LearningStatisticsDao,
    private val translationService: TranslationService,
    private val applicationScope: CoroutineScope
) {
    // 使用MutableStateFlow存储单词列表
    private val _allWords = MutableStateFlow<List<Word>>(emptyList())
    val allWords: StateFlow<List<Word>> = _allWords.asStateFlow()
    
    // 过滤后的单词列表流
    private val _filteredWords = MutableStateFlow<List<Word>>(emptyList())
    val filteredWords: StateFlow<List<Word>> = _filteredWords.asStateFlow()
    
    // 初始化，加载所有单词
    init {
        refreshAllWords()
    }
    
    // 刷新单词列表
    private fun refreshAllWords() {
        applicationScope.launch(Dispatchers.IO) {
            wordDao.getAllWordsFlow().collect { wordsList ->
                _allWords.value = wordsList
            }
        }
    }

    suspend fun insertWord(word: Word) = withContext(Dispatchers.IO) {
        if (word.chineseMeaning.isNullOrBlank()) {
            val translation = translationService.translateWord(word.word)
            wordDao.insertWord(word.copy(chineseMeaning = translation))
        } else {
            wordDao.insertWord(word)
        }
        refreshAllWords()
    }

    suspend fun insertWords(words: List<Word>) = withContext(Dispatchers.IO) {
        wordDao.insertWords(words)
        refreshAllWords()
    }

    suspend fun updateWord(word: Word) = withContext(Dispatchers.IO) {
        wordDao.updateWord(word)
        refreshAllWords()
    }

    suspend fun deleteWord(word: Word) = withContext(Dispatchers.IO) {
        wordDao.deleteWord(word)
        refreshAllWords()
    }

    suspend fun deleteWordByText(text: String) = withContext(Dispatchers.IO) {
        wordDao.deleteWordByText(text)
        refreshAllWords()
    }

    suspend fun getWordByText(text: String): Word? = withContext(Dispatchers.IO) {
        wordDao.getWordByText(text)
    }

    fun getAllWordsFlow(): Flow<List<Word>> {
        return wordDao.getAllWordsFlow()
    }

    // 添加缺失的方法
    fun getWordsByReviewTime(timeMillis: Long) {
        applicationScope.launch(Dispatchers.IO) {
            val allWordsList = wordDao.getAllWordsFlow().first()
            val filteredList = allWordsList.filter { word ->
                (word.lastReviewTime ?: 0L) <= timeMillis
            }
            _filteredWords.value = filteredList
        }
    }

    fun getWordsByFamiliarity(familiarity: Float) {
        applicationScope.launch(Dispatchers.IO) {
            val allWordsList = wordDao.getAllWordsFlow().first()
            val filteredList = allWordsList.filter { word ->
                word.familiarity <= familiarity
            }
            _filteredWords.value = filteredList
        }
    }

    fun getWordsByGroup(groupName: String) {
        applicationScope.launch(Dispatchers.IO) {
            val allWordsList = wordDao.getAllWordsFlow().first()
            val filteredList = allWordsList.filter { word ->
                word.group == groupName
            }
            _filteredWords.value = filteredList
        }
    }

    suspend fun getLearningStatistics(date: Long): LearningStatistics? = withContext(Dispatchers.IO) {
        learningStatisticsDao.getStatisticsByDate(date)
    }

    suspend fun updateLearningStatistics(statistics: LearningStatistics) = withContext(Dispatchers.IO) {
        learningStatisticsDao.insertOrUpdate(statistics)
    }
} 