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
class WordViewModel @Inject constructor(
    private val wordRepository: WordRepository
) : ViewModel() {
    
    // 使用MutableLiveData来存储单词列表
    private val _allWords = MutableLiveData<List<Word>>()
    val allWords: LiveData<List<Word>> = _allWords
    
    // 错误信息
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // 加载状态
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // 当前过滤器
    private val _currentGroup = MutableLiveData<String?>()
    val currentGroup: LiveData<String?> = _currentGroup
    
    // 初始化时加载所有单词
    init {
        loadAllWords()
    }
    
    private fun loadAllWords() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 使用StateFlow
                wordRepository.allWords.collectLatest { words ->
                    _allWords.value = words
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "加载单词失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun insertWord(word: Word) = viewModelScope.launch {
        try {
            wordRepository.insertWord(word)
        } catch (e: Exception) {
            _error.value = "添加单词失败: ${e.message}"
        }
    }
    
    fun insertWords(words: List<Word>) = viewModelScope.launch {
        try {
            wordRepository.insertWords(words)
        } catch (e: Exception) {
            _error.value = "批量添加单词失败: ${e.message}"
        }
    }
    
    fun addWords(wordsText: String) = viewModelScope.launch {
        val words = wordsText.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Word(word = it, meaning = "") }
        wordRepository.insertWords(words)
    }
    
    fun updateWord(word: Word) = viewModelScope.launch {
        wordRepository.updateWord(word)
    }
    
    /**
     * 更新单词的释义
     * @param word 单词
     * @param meaning 释义
     */
    fun updateWordMeaning(word: String, meaning: String) = viewModelScope.launch {
        // 先查找是否存在该单词
        val existingWord = wordRepository.getWordByText(word)
        if (existingWord != null) {
            // 如果存在，创建新对象并更新释义
            val updatedWord = existingWord.copy(meaning = meaning)
            wordRepository.updateWord(updatedWord)
        } else {
            // 如果不存在，创建新单词
            val newWord = Word(word = word, meaning = meaning)
            wordRepository.insertWord(newWord)
        }
    }
    
    /**
     * 批量删除单词
     * @param words 要删除的单词列表
     */
    fun deleteWords(words: List<Word>) = viewModelScope.launch {
        words.forEach { word ->
            wordRepository.deleteWord(word)
        }
    }
    
    fun deleteWord(word: Word) = viewModelScope.launch {
        wordRepository.deleteWord(word)
    }
    
    fun getWordsByGroup(groupName: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                _currentGroup.value = groupName
                // 使用StateFlow
                wordRepository.getWordsByGroup(groupName).collectLatest { words ->
                    _allWords.value = words
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "加载单词组失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }
}