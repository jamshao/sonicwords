package com.jamshao.sonicwords.ui.wordstudy

import android.text.format.DateUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.model.LearningStatistics
import com.jamshao.sonicwords.data.repository.LearningStatisticsRepository
import com.jamshao.sonicwords.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

@HiltViewModel
class WordStudyViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val statisticsRepository: LearningStatisticsRepository
) : ViewModel() {
    
    private val _words = MutableLiveData<List<Word>>()
    val words: LiveData<List<Word>> = _words
    
    private val _currentWordIndex = MutableLiveData<Int>()
    val currentWordIndex: LiveData<Int> = _currentWordIndex
    
    private val _currentLetterIndex = MutableLiveData<Int>(0)
    val currentLetterIndex: LiveData<Int> = _currentLetterIndex
    
    private val _currentWordErrorCount = MutableLiveData<Int>(0)
    val currentWordErrorCount: LiveData<Int> = _currentWordErrorCount
    
    private val _spellingState = MutableLiveData<SpellingState>(SpellingState.None)
    val spellingState: LiveData<SpellingState> = _spellingState
    
    private val _todayLearnedCount = MutableLiveData<Int>(0)
    val todayLearnedCount: LiveData<Int> = _todayLearnedCount
    
    private val _todayReviewCount = MutableLiveData<Int>(0)
    val todayReviewCount: LiveData<Int> = _todayReviewCount
    
    private val _todayStudyTimeMillis = MutableLiveData<Long>(0L)
    val todayStudyTime: LiveData<String> = _todayStudyTimeMillis.map { DateUtils.formatElapsedTime(it / 1000) }
    
    private var startTime: Long = 0L
    
    enum class SpellingState {
        None,           // 初始状态
        CorrectLetter,  // 字母拼写正确
        WrongLetter,    // 字母拼写错误
        CompleteWord    // 单词拼写完成
    }
    
    // 添加学习模式枚举
    enum class StudyMode {
        NEW_WORDS,       // 新单词学习模式
        REVIEW           // 复习模式
    }
    
    // 当前学习模式
    private val _currentMode = MutableLiveData<StudyMode>(StudyMode.NEW_WORDS)
    val currentMode: LiveData<StudyMode> = _currentMode
    
    init {
        loadWords()
        loadStatistics()
        startTime = System.currentTimeMillis()
    }
    
    /**
     * 设置学习模式
     */
    fun setStudyMode(mode: StudyMode) {
        if (_currentMode.value != mode) {
            _currentMode.value = mode
            // 切换模式后重新加载单词
            loadWords()
        }
    }
    
    /**
     * 切换学习模式
     */
    fun toggleStudyMode() {
        val newMode = if (_currentMode.value == StudyMode.NEW_WORDS) {
            StudyMode.REVIEW
        } else {
            StudyMode.NEW_WORDS
        }
        setStudyMode(newMode)
    }
    
    /**
     * 加载单词，根据当前模式决定加载新单词还是复习单词
     */
    private fun loadWords() {
        viewModelScope.launch {
            wordRepository.allWords.collectLatest { allWords ->
                val mode = _currentMode.value ?: StudyMode.NEW_WORDS
                
                val wordsToStudy = when (mode) {
                    StudyMode.NEW_WORDS -> {
                        // 新单词模式：加载未学过的单词
                        allWords.filter { !it.isLearned }
                    }
                    StudyMode.REVIEW -> {
                        // 复习模式：加载已学过的单词
                        allWords.filter { it.isLearned }
                    }
                }
                
                _words.value = wordsToStudy
                _currentWordIndex.value = 0
                _currentLetterIndex.value = 0
                _currentWordErrorCount.value = 0
            }
        }
    }
    
    private fun loadStatistics() {
        viewModelScope.launch {
            statisticsRepository.getTodayStatistics().collectLatest { stats ->
                _todayLearnedCount.value = stats?.learnedCount ?: 0
                _todayReviewCount.value = stats?.reviewCount ?: 0
                _todayStudyTimeMillis.value = stats?.studyTimeMillis ?: 0L
            }
        }
    }
    
    fun checkLetter(letter: String): Boolean {
        val currentWord = getCurrentWord() ?: return false
        val letterIndex = _currentLetterIndex.value ?: 0
        
        if (letterIndex >= currentWord.word.length) {
            return false
        }
        
        val expectedLetter = currentWord.word[letterIndex].toString().lowercase()
        val isCorrect = letter.trim().equals(expectedLetter, ignoreCase = true)
        
        if (isCorrect) {
            _spellingState.value = SpellingState.CorrectLetter
            return true
        } else {
            _spellingState.value = SpellingState.WrongLetter
            val errorCount = (_currentWordErrorCount.value ?: 0) + 1
            _currentWordErrorCount.value = errorCount
            
            if (errorCount >= 3) {
                markWordAsHard(currentWord)
            }
            return false
        }
    }
    
    fun nextLetter(): Boolean {
        val currentWord = getCurrentWord() ?: return false
        val letterIndex = (_currentLetterIndex.value ?: 0) + 1
        
        if (letterIndex >= currentWord.word.length) {
            _spellingState.value = SpellingState.CompleteWord
            _currentLetterIndex.value = 0
            _currentWordErrorCount.value = 0
            
            markWordAsKnown(currentWord)
            return true
        } else {
            _currentLetterIndex.value = letterIndex
            return false
        }
    }
    
    /**
     * 重置拼写状态
     */
    fun resetSpelling() {
        _currentLetterIndex.value = 0
        _currentWordErrorCount.value = 0
        _spellingState.value = SpellingState.None
    }
    
    /**
     * 移动到下一个单词
     */
    fun nextWord() {
        val totalWords = _words.value?.size ?: 0
        val currentIndex = _currentWordIndex.value ?: 0
        
        if (currentIndex < totalWords - 1) {
            _currentWordIndex.value = currentIndex + 1
            resetSpelling()
        } else {
            // 已经是最后一个单词
            // 可以考虑显示学习完成消息
        }
    }
    
    /**
     * 移动到上一个单词
     */
    fun previousWord() {
        val currentIndex = _currentWordIndex.value ?: 0
        
        if (currentIndex > 0) {
            _currentWordIndex.value = currentIndex - 1
            resetSpelling()
        } else {
            // 已经是第一个单词
        }
    }
    
    /**
     * 获取当前单词
     */
    fun getCurrentWord(): Word? {
        val wordList = _words.value ?: return null
        val index = _currentWordIndex.value ?: 0
        if (index < 0 || index >= wordList.size) {
            return null
        }
        return wordList[index]
    }
    
    fun getCurrentLetter(): String? {
        val currentWord = getCurrentWord() ?: return null
        val letterIndex = _currentLetterIndex.value ?: 0
        
        if (letterIndex >= currentWord.word.length) {
            return null
        }
        
        return currentWord.word[letterIndex].toString()
    }
    
    fun markWordAsKnown(word: Word) {
        viewModelScope.launch {
            val updatedWord = word.copy(isLearned = true, lastStudyTime = System.currentTimeMillis())
            wordRepository.updateWord(updatedWord)
            statisticsRepository.updateLearnedCount(1)
            nextWord()
        }
    }
    
    fun markWordAsUnknown(word: Word) {
        viewModelScope.launch {
            val updatedWord = word.copy(errorCount = word.errorCount + 1)
            wordRepository.updateWord(updatedWord)
            nextWord()
        }
    }
    
    fun markWordAsEasy(word: Word) {
        viewModelScope.launch {
            val updatedWord = word.copy(
                isLearned = true, 
                familiarity = 0.8f,
                lastStudyTime = System.currentTimeMillis()
            )
            wordRepository.updateWord(updatedWord)
            statisticsRepository.updateLearnedCount(1)
            nextWord()
        }
    }
    
    fun markWordAsMedium(word: Word) {
        viewModelScope.launch {
            val updatedWord = word.copy(
                familiarity = word.familiarity + 0.3f,
                lastStudyTime = System.currentTimeMillis()
            )
            wordRepository.updateWord(updatedWord)
            nextWord()
        }
    }
    
    fun markWordAsHard(word: Word) {
        viewModelScope.launch {
            val updatedWord = word.copy(
                familiarity = 0f,
                isLearned = false,
                errorCount = word.errorCount + 1
            )
            wordRepository.updateWord(updatedWord)
            
            // 更新学习统计
            updateStatistics()
        }
    }
    
    /**
     * 更新学习统计数据
     */
    private fun updateStatistics() {
        viewModelScope.launch {
            try {
                // 加载今日的学习统计数据
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                var statistics = statisticsRepository.getStatisticsByDate(today)
                
                if (statistics == null) {
                    // 如果今天没有统计数据，创建新的
                    statistics = LearningStatistics(
                        date = today,
                        learnedCount = 0,
                        reviewCount = 0,
                        correctCount = 0,
                        errorCount = 0,
                        studyTimeMillis = 0,
                        masteredCount = 0
                    )
                }
                
                // 更新统计数据
                val updatedStatistics = statistics.copy(
                    errorCount = statistics.errorCount + 1
                )
                
                // 保存更新后的统计数据
                statisticsRepository.updateStatistics(updatedStatistics)
            } catch (e: Exception) {
                Log.e("WordStudyViewModel", "更新学习统计失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 增加当前单词的错误计数
     */
    fun incrementErrorCount() {
        _currentWordErrorCount.value = (_currentWordErrorCount.value ?: 0) + 1
        
        // 获取当前单词，更新其错误计数
        val currentWord = getCurrentWord()
        if (currentWord != null) {
            viewModelScope.launch {
                val updatedWord = currentWord.copy(
                    errorCount = currentWord.errorCount + 1
                )
                wordRepository.updateWord(updatedWord)
            }
        }
    }
    
    fun updateStudyTime() {
        if (startTime > 0) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime > 0) {
                viewModelScope.launch {
                    statisticsRepository.updateStudyTime(elapsedTime)
                }
            }
            startTime = System.currentTimeMillis()
        } else {
            startTime = System.currentTimeMillis()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        updateStudyTime()
    }
    
    /**
     * 检查用户输入的完整单词是否正确
     * @param input 用户输入的单词
     * @return 是否正确
     */
    fun checkWholeWord(input: String): Boolean {
        val currentWord = getCurrentWord() ?: return false
        
        // 比较用户输入的单词和当前单词（忽略大小写）
        val isCorrect = input.trim().equals(currentWord.word.trim(), ignoreCase = true)
        
        if (isCorrect) {
            // 单词正确，更新拼写状态
            _spellingState.value = SpellingState.CompleteWord
            // 重置索引和错误计数
            _currentLetterIndex.value = 0
            _currentWordErrorCount.value = 0
            // 标记单词为已学会
            markWordAsKnown(currentWord)
            return true
        } else {
            // 单词错误，更新拼写状态
            _spellingState.value = SpellingState.WrongLetter
            // 增加错误计数
            val errorCount = (_currentWordErrorCount.value ?: 0) + 1
            _currentWordErrorCount.value = errorCount
            
            // 如果错误次数达到3次或以上，将单词标记为困难
            if (errorCount >= 3) {
                markWordAsHard(currentWord)
            }
            return false
        }
    }
    
    /**
     * 获取当前单词的全部字母
     * 用于测试时展示正确答案
     */
    fun getCorrectSpelling(): String? {
        return getCurrentWord()?.word
    }
}