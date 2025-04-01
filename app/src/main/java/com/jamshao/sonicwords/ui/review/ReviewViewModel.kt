package com.jamshao.sonicwords.ui.review

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.entity.LearningStatistics
import com.jamshao.sonicwords.data.repository.LearningStatisticsRepository
import com.jamshao.sonicwords.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val learningStatisticsRepository: LearningStatisticsRepository
) : ViewModel() {
    
    // 复习模式
    private val _reviewMode = MutableLiveData<ReviewMode>()
    val reviewMode: LiveData<ReviewMode> = _reviewMode

    // 复习单词列表
    private val _reviewWords = MutableLiveData<List<Word>>()
    val reviewWords: LiveData<List<Word>> = _reviewWords

    // 当前单词
    private val _currentWord = MutableLiveData<Word>()
    val currentWord: LiveData<Word> = _currentWord

    // 学习统计
    private val _reviewCount = MutableLiveData<Int>()
    val reviewCount: LiveData<Int> = _reviewCount

    private val _correctRate = MutableLiveData<Float>()
    val correctRate: LiveData<Float> = _correctRate

    private val _studyTime = MutableLiveData<Long>()
    val studyTime: LiveData<Long> = _studyTime

    private val _remainingWords = MutableLiveData<Int>()
    val remainingWords: LiveData<Int> = _remainingWords

    // 加载复习单词
    fun loadReviewWords(mode: ReviewMode) {
        _reviewMode.value = mode
        viewModelScope.launch {
            when (mode) {
                ReviewMode.SPACED_REPETITION -> loadSpacedRepetitionWords()
                ReviewMode.ERROR_PRIORITY -> loadErrorPriorityWords()
                ReviewMode.CUSTOM -> loadCustomWords()
                ReviewMode.MEMORY_CURVE -> loadMemoryCurveWords()
            }
        }
    }

    // 间隔重复复习
    private fun loadSpacedRepetitionWords() {
        val currentTime = System.currentTimeMillis()
        val wordsFlow = wordRepository.getWordsByReviewTime(currentTime)
        viewModelScope.launch {
            wordsFlow.collectLatest { words ->
                _reviewWords.value = words
                updateRemainingWords()
            }
        }
    }

    // 错误优先复习
    private fun loadErrorPriorityWords() {
        val wordsFlow = wordRepository.getWordsByFamiliarity(0f)
        viewModelScope.launch {
            wordsFlow.collectLatest { words ->
                // 按错误次数降序排序
                _reviewWords.value = words.sortedByDescending { it.errorCount }
                updateRemainingWords()
            }
        }
    }

    // 自定义复习
    private fun loadCustomWords() {
        val wordsFlow = wordRepository.allWords
        viewModelScope.launch {
            wordsFlow.collectLatest { words ->
                _reviewWords.value = words
                updateRemainingWords()
            }
        }
    }

    // 记忆曲线复习
    private fun loadMemoryCurveWords() {
        val currentTime = System.currentTimeMillis()
        val wordsFlow = wordRepository.getWordsByReviewTime(currentTime)
        viewModelScope.launch {
            wordsFlow.collectLatest { words ->
                _reviewWords.value = words.sortedBy { 
                    it.lastReviewTime ?: Long.MIN_VALUE 
                }
                updateRemainingWords()
            }
        }
    }

    // 添加更新当前单词的方法
    fun updateCurrentWord(position: Int) {
        val words = _reviewWords.value
        if (words != null && position < words.size) {
            _currentWord.value = words[position]
        }
    }

    // 更新单词学习状态
    fun updateWordStatus(word: Word, isCorrect: Boolean) {
        viewModelScope.launch {
            val updatedWord = word.copy(
                familiarity = if (isCorrect) word.familiarity + 0.1f else word.familiarity,
                errorCount = if (!isCorrect) word.errorCount + 1 else word.errorCount,
                lastReviewTime = System.currentTimeMillis()
            )
            wordRepository.updateWord(updatedWord)
            updateStatistics(isCorrect)
        }
    }

    // 更新学习统计
    private suspend fun updateStatistics(isCorrect: Boolean) {
        val today = LocalDateTime.now().toString().split("T")[0]
        val statistics = learningStatisticsRepository.getStatisticsByDate(today) 
            ?: com.jamshao.sonicwords.data.model.LearningStatistics(date = today)
        
        val updatedStatistics = statistics.copy(
            reviewCount = statistics.reviewCount + 1,
            correctCount = if (isCorrect) statistics.correctCount + 1 else statistics.correctCount,
            errorCount = if (!isCorrect) statistics.errorCount + 1 else statistics.errorCount
        )
        
        learningStatisticsRepository.updateStatistics(updatedStatistics)
    }

    // 更新剩余单词数
    private fun updateRemainingWords() {
        _remainingWords.value = _reviewWords.value?.size ?: 0
    }

    // 计算下次复习时间
    private fun calculateNextReviewTime(word: Word, isCorrect: Boolean): Long {
        val baseInterval = when (word.familiarity) {
            0f -> 1L * 60 * 60 * 1000 // 1小时
            1f -> 6L * 60 * 60 * 1000 // 6小时
            2f -> 24L * 60 * 60 * 1000 // 1天
            3f -> 72L * 60 * 60 * 1000 // 3天
            4f -> 168L * 60 * 60 * 1000 // 7天
            5f -> 336L * 60 * 60 * 1000 // 14天
            else -> 24L * 60 * 60 * 1000 // 默认1天
        }
        
        val interval = if (isCorrect) baseInterval * 2 else baseInterval / 2
        return System.currentTimeMillis() + interval
    }
} 