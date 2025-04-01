package com.jamshao.sonicwords.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    // 主题设置
    private val _themeMode = MutableStateFlow(sharedPreferences.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    
    // 语音设置
    private val _speechRate = MutableStateFlow(sharedPreferences.getInt("speech_rate", 50))
    val speechRate: StateFlow<Int> = _speechRate.asStateFlow()
    
    private val _speechPitch = MutableStateFlow(sharedPreferences.getInt("speech_pitch", 50))
    val speechPitch: StateFlow<Int> = _speechPitch.asStateFlow()
    
    private val _speechVolume = MutableStateFlow(sharedPreferences.getInt("speech_volume", 100))
    val speechVolume: StateFlow<Int> = _speechVolume.asStateFlow()
    
    // 在线TTS设置
    private val _useOnlineTTS = MutableStateFlow(sharedPreferences.getBoolean("use_online_tts", false))
    val useOnlineTTS: StateFlow<Boolean> = _useOnlineTTS.asStateFlow()
    
    private val _onlineTTSVoice = MutableStateFlow(sharedPreferences.getString("online_tts_voice", "FunAudioLLM/CosyVoice2-0.5B:charles") ?: "FunAudioLLM/CosyVoice2-0.5B:charles")
    val onlineTTSVoice: StateFlow<String> = _onlineTTSVoice.asStateFlow()
    
    // 学习设置
    private val _wordsPerDay = MutableStateFlow(sharedPreferences.getInt("words_per_day", 20))
    val wordsPerDay: StateFlow<Int> = _wordsPerDay.asStateFlow()
    
    private val _recognitionTimeout = MutableStateFlow(sharedPreferences.getInt("recognition_timeout", 5))
    val recognitionTimeout: StateFlow<Int> = _recognitionTimeout.asStateFlow()
    
    private val _autoPlay = MutableStateFlow(sharedPreferences.getBoolean("auto_play", true))
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()
    
    private val _autoNext = MutableStateFlow(sharedPreferences.getBoolean("auto_next", true))
    val autoNext: StateFlow<Boolean> = _autoNext.asStateFlow()
    
    // 更新设置
    fun updateThemeMode(mode: String) {
        sharedPreferences.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }
    
    fun updateSpeechRate(rate: Int) {
        sharedPreferences.edit().putInt("speech_rate", rate).apply()
        _speechRate.value = rate
    }
    
    fun updateSpeechPitch(pitch: Int) {
        sharedPreferences.edit().putInt("speech_pitch", pitch).apply()
        _speechPitch.value = pitch
    }
    
    fun updateSpeechVolume(volume: Int) {
        sharedPreferences.edit().putInt("speech_volume", volume).apply()
        _speechVolume.value = volume
    }
    
    fun updateUseOnlineTTS(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("use_online_tts", enabled).apply()
        _useOnlineTTS.value = enabled
    }
    
    fun updateOnlineTTSVoice(voice: String) {
        sharedPreferences.edit().putString("online_tts_voice", voice).apply()
        _onlineTTSVoice.value = voice
    }
    
    fun updateWordsPerDay(count: Int) {
        sharedPreferences.edit().putInt("words_per_day", count).apply()
        _wordsPerDay.value = count
    }
    
    fun updateRecognitionTimeout(timeout: Int) {
        sharedPreferences.edit().putInt("recognition_timeout", timeout).apply()
        _recognitionTimeout.value = timeout
    }
    
    fun updateAutoPlay(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("auto_play", enabled).apply()
        _autoPlay.value = enabled
    }
    
    fun updateAutoNext(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("auto_next", enabled).apply()
        _autoNext.value = enabled
    }
} 