package com.jamshao.sonicwords.service

/**
 * 翻译服务接口，用于翻译单词
 */
interface TranslationService {
    /**
     * 将英文单词翻译为中文
     * @param word 要翻译的英文单词
     * @return 翻译后的中文意思
     */
    suspend fun translateWord(word: String): String
} 