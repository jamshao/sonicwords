package com.jamshao.sonicwords.service.impl

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.jamshao.sonicwords.service.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MLKitTranslationService @Inject constructor() : TranslationService {

    private val TAG = "MLKitTranslation"

    // 创建英文到中文的翻译器
    private val translatorOptions = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.CHINESE)
        .build()

    private val translator: Translator by lazy {
        Translation.getClient(translatorOptions)
    }

    override suspend fun translateWord(word: String): String {
        return withContext(Dispatchers.IO) {
            try {
                ensureModelDownloaded()
                translateTextInternal(word)
            } catch (e: Exception) {
                Log.e(TAG, "翻译失败: ${e.message}", e)
                // 翻译失败时返回原始单词
                "无法翻译: $word"
            }
        }
    }
    
    /**
     * 确保翻译模型已下载
     */
    private suspend fun ensureModelDownloaded() = suspendCancellableCoroutine { continuation ->
        val conditions = DownloadConditions.Builder()
            .requireWifi() // 仅在WIFI下下载模型，或者可以移除此限制
            .build()
        
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                Log.i(TAG, "翻译模型下载成功")
                continuation.resume(Unit)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "翻译模型下载失败: ${exception.message}", exception)
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
        
        continuation.invokeOnCancellation {
            // 清理资源
            Log.d(TAG, "模型下载已取消")
        }
    }
    
    /**
     * 执行实际的文本翻译
     */
    private suspend fun translateTextInternal(text: String): String = suspendCancellableCoroutine { continuation ->
        translator.translate(text)
            .addOnSuccessListener { translatedText ->
                Log.d(TAG, "翻译成功: $text -> $translatedText")
                if (continuation.isActive) {
                    continuation.resume(translatedText)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "翻译失败: ${exception.message}", exception)
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
        
        continuation.invokeOnCancellation {
            // 清理资源
            Log.d(TAG, "翻译操作已取消")
        }
    }
    
    /**
     * 关闭并释放翻译器资源
     * 应在不需要翻译器时调用此方法，例如在应用退出时
     */
    fun close() {
        translator.close()
    }
} 