package com.jamshao.sonicwords.utils

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * TTS帮助类，用于管理本地和在线TTS服务
 */
class TTSHelper(private val context: Context) {
    private val TAG = "TTSHelper"
    
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var textToSpeech: TextToSpeech? = null
    private var onlineTTSService: OnlineTTSService? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    private var isTTSInitialized = false
    private var pendingText: String? = null
    private var pendingCallback: (() -> Unit)? = null
    
    /**
     * 初始化TTS服务
     */
    fun init() {
        // 初始化本地TTS
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "语言不支持")
                } else {
                    isTTSInitialized = true
                    updateTTSSettings()
                    pendingText?.let { text ->
                        speak(text, pendingCallback)
                        pendingText = null
                        pendingCallback = null
                    }
                }
            } else {
                Log.e(TAG, "TTS初始化失败")
            }
        }
        
        // 初始化在线TTS
        onlineTTSService = OnlineTTSService(context)
    }
    
    /**
     * 根据设置更新TTS参数
     */
    private fun updateTTSSettings() {
        val speechRate = sharedPreferences.getInt("speech_rate", 50)
        val speechPitch = sharedPreferences.getInt("speech_pitch", 50)
        val speechVolume = sharedPreferences.getInt("speech_volume", 100)
        
        // 转换语速和音调范围从0-100到TTS范围0.5-2.0
        val actualRate = 0.5f + (speechRate / 100f * 1.5f)
        val actualPitch = 0.5f + (speechPitch / 100f * 1.5f)
        
        textToSpeech?.setSpeechRate(actualRate)
        textToSpeech?.setPitch(actualPitch)
        
        // 设置音量
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = (maxVolume * speechVolume / 100f).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
    
    /**
     * 播放文本
     * @param text 要播放的文本
     * @param onComplete 播放完成回调
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }
        
        // 停止当前播放
        stop()
        
        // 判断是否使用在线TTS
        val useOnlineTTS = sharedPreferences.getBoolean("use_online_tts", false)
        
        if (useOnlineTTS) {
            // 使用在线TTS
            speakWithOnlineTTS(text, onComplete)
        } else {
            // 使用本地TTS
            speakWithLocalTTS(text, onComplete)
        }
    }
    
    /**
     * 使用本地TTS播放
     */
    private fun speakWithLocalTTS(text: String, onComplete: (() -> Unit)? = null) {
        if (!isTTSInitialized) {
            pendingText = text
            pendingCallback = onComplete
            return
        }
        
        // 更新TTS设置
        updateTTSSettings()
        
        // 设置完成回调
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            
            override fun onDone(utteranceId: String?) {
                onComplete?.invoke()
            }
            
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS播放错误: $utteranceId")
                onComplete?.invoke()
            }
        })
        
        // 播放文本
        val utteranceId = UUID.randomUUID().toString()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    
    /**
     * 使用在线TTS播放
     */
    private fun speakWithOnlineTTS(text: String, onComplete: (() -> Unit)? = null) {
        onlineTTSService?.let { service ->
            coroutineScope.launch {
                try {
                    service.speak(text) {
                        onComplete?.invoke()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "在线TTS播放失败", e)
                    // 失败时尝试使用本地TTS
                    speakWithLocalTTS(text, onComplete)
                }
            }
        }
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        textToSpeech?.stop()
        onlineTTSService?.stop()
    }
    
    /**
     * 释放资源
     */
    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTTSInitialized = false
    }
} 