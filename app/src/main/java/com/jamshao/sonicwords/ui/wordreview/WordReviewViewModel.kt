package com.jamshao.sonicwords.ui.wordreview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordReviewViewModel @Inject constructor(
    private val wordRepository: WordRepository
) : ViewModel() {
    
    private val _reviewWords = MutableLiveData<List<Word>>()
    val reviewWords: LiveData<List<Word>> = _reviewWords
    
    private val _currentWord = MutableLiveData<Word>()
    val currentWord: LiveData<Word> = _currentWord
    
    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    fun loadReviewWords() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                wordRepository.getWordsByReviewTime(System.currentTimeMillis())
                    .collectLatest { words ->
                        _reviewWords.value = words
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = "加载复习单词失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun updateCurrentWord(position: Int) {
        _currentPosition.value = position
        _currentWord.value = _reviewWords.value?.getOrNull(position)
    }
    
    fun markWordAsKnown(word: Word) {
        viewModelScope.launch {
            try {
                val updatedWord = word.copy(
                    familiarity = (word.familiarity + 0.1f).coerceIn(0f, 1f),
                    lastReviewTime = System.currentTimeMillis()
                )
                wordRepository.updateWord(updatedWord)
            } catch (e: Exception) {
                _error.value = "更新单词状态失败: ${e.message}"
            }
        }
    }
    
    fun markWordAsUnknown(word: Word) {
        viewModelScope.launch {
            try {
                val updatedWord = word.copy(
                    familiarity = (word.familiarity - 0.1f).coerceIn(0f, 1f),
                    lastReviewTime = System.currentTimeMillis()
                )
                wordRepository.updateWord(updatedWord)
            } catch (e: Exception) {
                _error.value = "更新单词状态失败: ${e.message}"
            }
        }
    }
}