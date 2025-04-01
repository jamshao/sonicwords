package com.jamshao.sonicwords.ui.wordstudy

import android.text.format.DateUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.repository.LearningStatisticsRepository
import com.jamshao.sonicwords.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordStudyViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val statisticsRepository: LearningStatisticsRepository
) : ViewModel() {
    
    private val _words = MutableLiveData<List<Word>>()
    val words: LiveData<List<Word>> = _words
    
    private val _currentWordIndex = MutableLiveData<Int>()
    val currentWordIndex: LiveData<Int> = _currentWordIndex
    
    private val _todayLearnedCount = MutableLiveData<Int>(0)
    val todayLearnedCount: LiveData<Int> = _todayLearnedCount
    
    private val _todayReviewCount = MutableLiveData<Int>(0)
    val todayReviewCount: LiveData<Int> = _todayReviewCount
    
    private val _todayStudyTimeMillis = MutableLiveData<Long>(0L)
    val todayStudyTime: LiveData<String> = _todayStudyTimeMillis.map { DateUtils.formatElapsedTime(it / 1000) }
    
    private var startTime: Long = 0L
    
    init {
        loadStudyWords()
        loadStatistics()
        startTime = System.currentTimeMillis()
    }
    
    private fun loadStudyWords() {
        viewModelScope.launch {
            wordRepository.allWords.collectLatest { allWords ->
                val wordsToStudy = allWords.filter { !it.isLearned }
                _words.value = wordsToStudy
                _currentWordIndex.value = 0
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
                familiarity = word.familiarity + 0.1f,
                errorCount = word.errorCount + 1,
                lastStudyTime = System.currentTimeMillis()
            )
            wordRepository.updateWord(updatedWord)
            nextWord()
        }
    }
    
    private fun nextWord() {
        val currentIndex = _currentWordIndex.value ?: 0
        val wordList = _words.value ?: emptyList()
        if (currentIndex < wordList.size - 1) {
            _currentWordIndex.value = currentIndex + 1
        } else {
            // TODO: Handle end of word list
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
}