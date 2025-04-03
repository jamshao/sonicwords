package com.jamshao.sonicwords.ui.wordlist

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
class WordListViewModel @Inject constructor(
    private val wordRepository: WordRepository
) : ViewModel() {
    
    val allWords: StateFlow<List<Word>> = wordRepository.allWords
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    fun loadAllWords() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                wordRepository.allWords.collectLatest { _ ->
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "加载单词失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun insertWord(word: Word) {
        viewModelScope.launch {
            try {
                wordRepository.insertWord(word)
            } catch (e: Exception) {
                _error.value = "添加单词失败: ${e.message}"
            }
        }
    }
    
    fun insertWords(words: List<Word>) {
        viewModelScope.launch {
            try {
                wordRepository.insertWords(words)
            } catch (e: Exception) {
                _error.value = "批量添加单词失败: ${e.message}"
            }
        }
    }
    
    fun updateWord(word: Word) {
        viewModelScope.launch {
            try {
                wordRepository.updateWord(word)
            } catch (e: Exception) {
                _error.value = "更新单词失败: ${e.message}"
            }
        }
    }
    
    fun deleteWord(word: Word) {
        viewModelScope.launch {
            try {
                wordRepository.deleteWord(word)
            } catch (e: Exception) {
                _error.value = "删除单词失败: ${e.message}"
            }
        }
    }
    
    fun getWordsByGroup(groupName: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                wordRepository.getWordsByGroup(groupName).collectLatest { _ ->
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "加载单词组失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }
} 