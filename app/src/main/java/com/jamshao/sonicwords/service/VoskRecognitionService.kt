package com.jamshao.sonicwords.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoskRecognitionService @Inject constructor(
    private val context: Context
) {
    private val TAG = "VoskRecognitionService"
    
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isInitialized = false
    private var currentWords = ArrayList<String>()
    
    /**
     * 初始化Vosk语音识别器
     * @return 是否初始化成功
     */
    suspend fun initializeRecognizer(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && model != null) return@withContext true
        
        try {
            // 从assets目录同步模型
            val modelDir = File(context.getExternalFilesDir(null), "model-en")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
                // 这里应该复制assets/model-en下的文件到modelDir
                // TODO: 实现完整的assets目录同步
                Log.d(TAG, "模型目录已创建: ${modelDir.absolutePath}")
            }
            
            model = Model(modelDir.absolutePath)
            isInitialized = true
            Log.i(TAG, "Vosk模型加载成功")
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "Vosk模型初始化失败: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 开始语音识别
     */
    fun startListening(listener: RecognitionListener) {
        if (speechService != null) {
            try {
                speechService?.stop()
                speechService = null
            } catch (e: Exception) {
                Log.e(TAG, "停止语音服务失败: ${e.message}", e)
            }
        }
        
        if (model == null) {
            Log.e(TAG, "Vosk模型未初始化")
            return
        }
        
        try {
            // 创建识别器，特别配置为单词模式
            val recognizer = if (currentWords.isNotEmpty()) {
                // 使用配置的词汇表
                val grammar = buildGrammar(currentWords)
                Recognizer(model, 16000.0f, grammar)
            } else {
                // 使用默认模式
                Recognizer(model, 16000.0f)
            }
            
            // 创建语音服务
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(listener)
            
            Log.i(TAG, "Vosk语音识别开始")
        } catch (e: Exception) {
            Log.e(TAG, "启动Vosk识别失败: ${e.message}", e)
        }
    }
    
    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (speechService != null) {
            try {
                speechService?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "停止语音识别失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 关闭资源
     */
    fun shutdown() {
        try {
            if (speechService != null) {
                speechService?.shutdown()
                speechService = null
            }
            
            if (model != null) {
                model?.close()
                model = null
            }
            
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "关闭Vosk资源失败: ${e.message}", e)
        }
    }
    
    /**
     * 配置Vosk词汇表用于提高识别率
     * @param words 需要识别的单词列表
     */
    fun configureVocabulary(words: List<String>) {
        if (words.isEmpty()) return
        
        try {
            currentWords.clear()
            for (word in words) {
                currentWords.add(word.lowercase().trim())
            }
            Log.d(TAG, "配置词汇表: ${currentWords.size}个单词")
        } catch (e: Exception) {
            Log.e(TAG, "配置词汇表失败: ${e.message}", e)
        }
    }
    
    /**
     * 构建Vosk词汇表格式
     */
    private fun buildGrammar(words: List<String>): String {
        val sb = StringBuilder("[\"")
        for (i in words.indices) {
            sb.append(words[i])
            if (i < words.size - 1) {
                sb.append("\", \"")
            }
        }
        sb.append("\"]")
        return sb.toString()
    }
    
    val isReady: Boolean
        get() = isInitialized && model != null
} 