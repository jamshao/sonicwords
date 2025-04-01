package com.jamshao.sonicwords.utils

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 在线语音合成服务
 */
class OnlineTTSService(private val context: Context) {
    private val TAG = "OnlineTTSService"
    private val API_URL = "https://api.siliconflow.cn/v1/audio/speech"
    private val API_KEY = "sk-qgsevzdqmvwowiyjxlbittfvdbrzzxzeftbqfkcvagaicrlr"
    private val MODEL = "FunAudioLLM/CosyVoice2-0.5B"
    
    private val client = OkHttpClient.Builder()
        .build()
    
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val cacheDir = File(context.cacheDir, "tts_cache")
    private var mediaPlayer: MediaPlayer? = null
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * 清理不再使用的缓存文件
     */
    fun cleanupCache() {
        try {
            // 保留最近使用的10个文件
            val files = cacheDir.listFiles()
            if (files != null && files.size > 10) {
                files.sortBy { it.lastModified() }
                for (i in 0 until files.size - 10) {
                    files[i].delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败", e)
        }
    }
    
    /**
     * 停止当前播放
     */
    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
    
    /**
     * 使用在线语音合成播放文本
     * @param text 要播放的文本
     * @param onComplete 播放完成的回调
     * @return 是否成功开始播放
     */
    suspend fun speak(text: String, onComplete: () -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext false
            }
            
            // 停止当前播放
            stop()
            
            // 获取当前设置的音色
            val voice = sharedPreferences.getString("online_tts_voice", "FunAudioLLM/CosyVoice2-0.5B:charles") ?: "FunAudioLLM/CosyVoice2-0.5B:charles"
            
            // 生成缓存文件名 (使用文本和音色的组合作为唯一标识)
            val cacheKey = "${text.hashCode()}_${voice.hashCode()}"
            val cacheFile = File(cacheDir, "$cacheKey.mp3")
            
            // 检查缓存
            if (cacheFile.exists()) {
                playFromFile(cacheFile, onComplete)
                return@withContext true
            }
            
            // 准备请求数据
            val jsonBody = JSONObject().apply {
                put("model", MODEL)
                put("voice", voice)
                put("input", text)
                put("response_format", "mp3")
            }
            
            val request = Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer $API_KEY")
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            // 发送请求
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API请求失败: ${response.code} - ${response.message}")
                return@withContext false
            }
            
            // 保存响应到缓存文件
            response.body?.let { body ->
                FileOutputStream(cacheFile).use { outputStream ->
                    body.byteStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // 播放生成的音频
                playFromFile(cacheFile, onComplete)
                
                // 清理旧的缓存文件
                cleanupCache()
                
                return@withContext true
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "语音合成失败", e)
            return@withContext false
        }
    }
    
    /**
     * 从文件播放音频
     */
    private fun playFromFile(file: File, onComplete: () -> Unit) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                onComplete()
                release()
                mediaPlayer = null
            }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer错误: $what, $extra")
                release()
                mediaPlayer = null
                false
            }
            prepare()
            start()
        }
    }
} 