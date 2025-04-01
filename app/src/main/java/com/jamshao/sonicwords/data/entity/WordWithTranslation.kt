package com.jamshao.sonicwords.data.entity

/**
 * 单词及其翻译，用于单词输入界面
 */
data class WordWithTranslation(
    val word: String,
    val translation: String,
    val isExisting: Boolean = false,
    val error: String? = null
) 