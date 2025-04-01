package com.jamshao.sonicwords.ui.word

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordViewModel @Inject constructor(
    private val wordRepository: WordRepository
) : ViewModel() {

    private val _word = MutableLiveData<Word>()
    val word: LiveData<Word> = _word

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _translationStatus = MutableLiveData<String?>()
    val translationStatus: LiveData<String?> = _translationStatus

    fun loadWord(word: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                wordRepository.getWordByText(word)?.let { word ->
                    _word.value = word
                }
            } catch (e: Exception) {
                _error.value = "加载单词失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateWord(word: Word) {
        viewModelScope.launch {
            try {
                wordRepository.updateWord(word)
                _word.value = word
            } catch (e: Exception) {
                _error.value = "更新单词失败: ${e.message}"
            }
        }
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            try {
                wordRepository.deleteWord(word)
                _word.value = null
            } catch (e: Exception) {
                _error.value = "删除单词失败: ${e.message}"
            }
        }
    }

    fun loadWords() {
        viewModelScope.launch {
            try {
                val allWords = wordRepository.getWordsByGroup("default").value
                _word.value = allWords.firstOrNull()
            } catch (e: Exception) {
                _error.value = "加载单词失败：${e.message}"
            }
        }
    }

    fun addWord(word: String, meaning: String, chineseMeaning: String? = null) {
        viewModelScope.launch {
            try {
                val newWord = Word(
                    word = word,
                    meaning = meaning,
                    chineseMeaning = chineseMeaning,
                    familiarity = 0f,
                    errorCount = 0,
                    isLearned = false
                )
                wordRepository.insertWord(newWord)
                loadWords()
            } catch (e: Exception) {
                _error.value = "添加单词失败：${e.message}"
            }
        }
    }

    fun translateWord(word: String) {
        viewModelScope.launch {
            try {
                _translationStatus.value = "正在翻译..."
                // TODO: 实现翻译功能
                _translationStatus.value = null
            } catch (e: Exception) {
                _error.value = "翻译失败：${e.message}"
                _translationStatus.value = null
            }
        }
    }
} 