package com.jamshao.sonicwords.ui.input

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.entity.WordWithTranslation
import com.jamshao.sonicwords.data.repository.WordRepository
import com.jamshao.sonicwords.service.TranslationService
import com.jamshao.sonicwords.utils.TextUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class WordInputViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val translationService: TranslationService
) : ViewModel() {

    private val _translations = MutableLiveData<List<WordWithTranslation>>(emptyList())
    val translations: LiveData<List<WordWithTranslation>> = _translations

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _ocrResult = MutableLiveData<String?>(null)
    val ocrResult: LiveData<String?> = _ocrResult

    // 当前缓存的单词列表
    private val currentWordsList = mutableListOf<WordWithTranslation>()

    // 处理用户手动输入的文本
    fun processInput(text: String) {
        if (text.isBlank()) {
            _errorMessage.value = "请输入单词"
            return
        }

        _isLoading.value = true

        val words = TextUtils.parseInputText(text)
        if (words.isEmpty()) {
            _isLoading.value = false
            _errorMessage.value = "未能解析出任何单词"
            return
        }

        translateWords(words)
    }

    /**
     * 仅解析输入文本为单词列表，不进行翻译
     * 用于单词选择对话框
     */
    fun parseInputTextToWords(text: String): List<String> {
        if (text.isBlank()) {
            return emptyList()
        }
        
        return TextUtils.parseInputText(text)
    }

    // 翻译单词列表
    private fun translateWords(words: List<String>) {
        viewModelScope.launch {
            try {
                words.forEach { word ->
                    try {
                        val translation = translationService.translateWord(word)
                        val wordWithTranslation = WordWithTranslation(
                            word = word,
                            translation = translation
                        )
                        if (!currentWordsList.any { it.word == word }) {
                            currentWordsList.add(wordWithTranslation)
                        }
                    } catch (e: Exception) {
                        // 添加单词但标记翻译失败
                        val wordWithTranslation = WordWithTranslation(
                            word = word,
                            translation = "翻译失败"
                        )
                        if (!currentWordsList.any { it.word == word }) {
                            currentWordsList.add(wordWithTranslation)
                        }
                    }
                }
                
                _translations.value = currentWordsList.toList()
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "翻译过程中出错: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // 处理OCR识别结果
    fun processOcrResult(text: String) {
        _ocrResult.value = text
        // processInput会在UI中观察ocrResult后被调用
    }
    
    /**
     * 从OCR识别文本中提取英文单词
     */
    fun extractWordsFromOcr(text: String): List<String> {
        return TextUtils.extractEnglishWords(text)
    }

    // 编辑单词翻译
    fun editTranslation(wordTranslation: WordWithTranslation, newTranslation: String) {
        val index = currentWordsList.indexOfFirst { it.word == wordTranslation.word }
        if (index != -1) {
            currentWordsList[index] = WordWithTranslation(
                word = wordTranslation.word,
                translation = newTranslation
            )
            _translations.value = currentWordsList.toList()
        }
    }

    // 移除单词
    fun removeWord(wordTranslation: WordWithTranslation) {
        currentWordsList.removeIf { it.word == wordTranslation.word }
        _translations.value = currentWordsList.toList()
    }

    // 保存单词到数据库
    fun saveWords() {
        if (currentWordsList.isEmpty()) {
            _errorMessage.value = "没有单词可保存"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val currentTime = Date().time
                currentWordsList.forEach { wordTranslation ->
                    val word = Word(
                        id = 0, // 数据库会自动生成ID
                        word = wordTranslation.word,
                        meaning = wordTranslation.translation,
                        chineseMeaning = wordTranslation.translation,
                        familiarity = 0f,
                        errorCount = 0,
                        isLearned = false,
                        lastStudyTime = currentTime,
                        lastReviewTime = 0,
                        group = "未分组" // 默认组名
                    )
                    wordRepository.insertWord(word)
                }
                
                // 清空当前列表
                currentWordsList.clear()
                _translations.value = emptyList()
                _isLoading.value = false
                _errorMessage.value = "单词保存成功"
            } catch (e: Exception) {
                _errorMessage.value = "保存单词时出错: ${e.message}"
                _isLoading.value = false
            }
        }
    }
} 