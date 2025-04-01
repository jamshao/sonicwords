package com.jamshao.sonicwords.data.repository

import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.jamshao.sonicwords.data.api.TranslationService
import com.jamshao.sonicwords.data.dao.WordDao
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.data.entity.WordWithTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor(
    private val wordDao: WordDao
) {
    // 使用ML Kit翻译器
    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
        .setTargetLanguage(com.google.mlkit.nl.translate.TranslateLanguage.CHINESE)
        .build()
    private val translator = Translation.getClient(options)
    private var isModelDownloaded = false
    
    // 备用的网络翻译服务
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://translate.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val translationService = retrofit.create(TranslationService::class.java)
    
    /**
     * 检查翻译模型是否已下载
     */
    suspend fun checkModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        if (!isModelDownloaded) {
            try {
                translator.downloadModelIfNeeded().await()
                isModelDownloaded = true
            } catch (e: Exception) {
                // 下载失败，使用网络翻译作为备选
            }
        }
        return@withContext isModelDownloaded
    }
    
    /**
     * 翻译单词
     */
    suspend fun translateWord(word: String): WordWithTranslation = withContext(Dispatchers.IO) {
        // 首先检查数据库中是否已有该单词
        val existingWord = wordDao.findWordByText(word)
        if (existingWord != null) {
            return@withContext WordWithTranslation(
                word = existingWord.word,
                translation = existingWord.chineseMeaning ?: existingWord.meaning,
                isExisting = true
            )
        }
        
        // 尝试使用ML Kit翻译
        try {
            if (checkModelDownloaded()) {
                val translation = translator.translate(word).await()
                return@withContext WordWithTranslation(
                    word = word,
                    translation = translation
                )
            }
            
            // 如果ML Kit模型未下载或翻译失败，尝试使用网络翻译
            val response = translationService.translate(text = word)
            val translation = response[0][0][0]
            
            return@withContext WordWithTranslation(
                word = word,
                translation = translation
            )
        } catch (e: Exception) {
            // 翻译失败
            return@withContext WordWithTranslation(
                word = word,
                translation = "", // 添加空翻译
                error = e.message ?: "翻译失败"
            )
        }
    }
    
    /**
     * 保存单词到数据库
     */
    suspend fun saveWord(wordTranslation: WordWithTranslation) = withContext(Dispatchers.IO) {
        val word = Word(
            word = wordTranslation.word,
            meaning = wordTranslation.word, // 英文解释默认为单词本身
            chineseMeaning = wordTranslation.translation
        )
        wordDao.insertWord(word)
    }
    
    /**
     * 更新单词翻译
     */
    suspend fun updateWordTranslation(word: String, translation: String) = withContext(Dispatchers.IO) {
        val existingWord = wordDao.findWordByText(word)
        if (existingWord != null) {
            val updatedWord = existingWord.copy(chineseMeaning = translation)
            wordDao.updateWord(updatedWord)
        }
    }
} 