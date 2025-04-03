package com.jamshao.sonicwords.ui.settings

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamshao.sonicwords.R
import com.jamshao.sonicwords.databinding.FragmentSettingsBinding
import com.jamshao.sonicwords.utils.DataBackupUtils
import com.jamshao.sonicwords.utils.OnlineTTSService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sharedPreferences: SharedPreferences
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val settingsScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var backupFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var restoreFileLauncher: ActivityResultLauncher<Intent>
    
    private lateinit var onlineTTSService: OnlineTTSService

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        onlineTTSService = OnlineTTSService(requireContext())
        
        // 初始化摘要
        updateWordsPerDaySummary()
        updateRecognitionTimeoutSummary()
        updateSpeechRateSummary()
        updateSpeechVolumeSummary()
        updateThemeSummary()
        updateOnlineTTSVoiceSummary()
        
        // 设置主题切换监听
        findPreference<ListPreference>("theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
            val theme = when (newValue.toString()) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(theme)
            true
        }
        
        // 在线TTS切换监听
        findPreference<SwitchPreferenceCompat>("use_online_tts")?.setOnPreferenceChangeListener { _, newValue ->
            settingsViewModel.updateUseOnlineTTS(newValue as Boolean)
            true
        }
        
        // 在线TTS音色选择监听
        findPreference<ListPreference>("online_tts_voice")?.setOnPreferenceChangeListener { _, newValue ->
            settingsViewModel.updateOnlineTTSVoice(newValue.toString())
            updateOnlineTTSVoiceSummary()
            
            // 测试播放所选音色
            testOnlineTTSVoice(newValue.toString())
            
            true
        }
        
        // 设置备份数据点击事件
        findPreference<Preference>("backup_data")?.setOnPreferenceClickListener {
            startBackupProcess()
            true
        }
        
        // 设置恢复数据点击事件
        findPreference<Preference>("restore_data")?.setOnPreferenceClickListener {
            startRestoreProcess()
            true
        }
        
        // 设置清除数据点击事件
        findPreference<Preference>("clear_data")?.setOnPreferenceClickListener {
            showClearDataDialog()
            true
        }
        
        // 设置版本信息
        val versionPreference = findPreference<Preference>("version")
        val versionName = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        versionPreference?.summary = "SonicWords v$versionName"
        
        // 设置反馈点击事件
        findPreference<Preference>("feedback")?.setOnPreferenceClickListener {
            sendFeedback()
            true
        }
        
        // 注册文件选择器
        registerFileLaunchers()
    }
    
    private fun testOnlineTTSVoice(voiceId: String) {
        settingsScope.launch {
            val testText = "这是一个测试，看看这个声音怎么样？"
            Log.d("SettingsFragment", "测试在线TTS声音: $voiceId")
            onlineTTSService.speak(testText)
        }
    }
    
    private fun updateOnlineTTSVoiceSummary() {
        val preference = findPreference<ListPreference>("online_tts_voice")
        val entries = resources.getStringArray(R.array.online_tts_voice_entries)
        val values = resources.getStringArray(R.array.online_tts_voice_values)
        
        val voiceValue = sharedPreferences.getString("online_tts_voice", "FunAudioLLM/CosyVoice2-0.5B:charles") ?: "FunAudioLLM/CosyVoice2-0.5B:charles"
        val index = values.indexOf(voiceValue)
        val summary = if (index >= 0) entries[index] else "未知音色"
        
        preference?.summary = summary
    }
    
    private fun registerFileLaunchers() {
        backupFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    settingsScope.launch {
                        try {
                            val success = DataBackupUtils.backupData(requireContext(), uri)
                            if (success) {
                                Toast.makeText(requireContext(), "备份成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), "备份失败", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsFragment", "备份失败", e)
                            Toast.makeText(requireContext(), "备份失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        
        restoreFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    settingsScope.launch {
                        try {
                            val success = DataBackupUtils.restoreData(requireContext(), uri)
                            if (success) {
                                Toast.makeText(requireContext(), "恢复成功", Toast.LENGTH_SHORT).show()
                                // 重新加载设置
                                updateWordsPerDaySummary()
                                updateRecognitionTimeoutSummary()
                                updateSpeechRateSummary()
                                updateSpeechVolumeSummary()
                                updateThemeSummary()
                                updateOnlineTTSVoiceSummary()
                            } else {
                                Toast.makeText(requireContext(), "恢复失败", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsFragment", "恢复失败", e)
                            Toast.makeText(requireContext(), "恢复失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }
    
    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        
        // 停止在线TTS服务
        try {
            onlineTTSService.stop()
        } catch (e: Exception) {
            Log.e("SettingsFragment", "停止在线TTS服务失败", e)
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // 确保TTS服务已停止
        try {
            onlineTTSService.stop()
        } catch (e: Exception) {
            Log.e("SettingsFragment", "停止在线TTS服务失败", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 确保已停止并释放所有资源
        try {
            onlineTTSService.stop()
        } catch (e: Exception) {
            Log.e("SettingsFragment", "销毁时停止在线TTS服务失败", e)
        }
    }
    
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "words_per_day" -> updateWordsPerDaySummary()
            "recognition_timeout" -> updateRecognitionTimeoutSummary()
            "speech_rate" -> updateSpeechRateSummary()
            "speech_volume" -> updateSpeechVolumeSummary()
            "theme_mode" -> updateThemeSummary()
            "online_tts_voice" -> updateOnlineTTSVoiceSummary()
        }
    }
    
    private fun updateWordsPerDaySummary() {
        val preference = findPreference<SeekBarPreference>("words_per_day")
        val wordsValue = sharedPreferences.getInt("words_per_day", 20)
        preference?.summary = "每天学习 $wordsValue 个单词"
    }
    
    private fun updateRecognitionTimeoutSummary() {
        val preference = findPreference<SeekBarPreference>("recognition_timeout")
        val timeoutValue = sharedPreferences.getInt("recognition_timeout", 5)
        preference?.summary = "语音识别超时时间：${timeoutValue}秒"
    }
    
    private fun updateSpeechRateSummary() {
        val preference = findPreference<SeekBarPreference>("speech_rate")
        val rateValue = sharedPreferences.getInt("speech_rate", 50)
        val actualRate = 0.5f + (rateValue / 100f)
        preference?.summary = "语音速度：${String.format("%.2f", actualRate)}"
    }
    
    private fun updateSpeechVolumeSummary() {
        val preference = findPreference<SeekBarPreference>("speech_volume")
        val volumeValue = sharedPreferences.getInt("speech_volume", 100)
        preference?.summary = "音量：$volumeValue%"
    }
    
    private fun updateThemeSummary() {
        val preference = findPreference<ListPreference>("theme_mode")
        val themeValue = sharedPreferences.getString("theme_mode", "system")
        val themeSummary = when (themeValue) {
            "light" -> "浅色主题"
            "dark" -> "深色主题"
            else -> "跟随系统"
        }
        preference?.summary = themeSummary
    }
    
    private fun startBackupProcess() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "sonicwords_backup_$timestamp.zip"
        
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        
        try {
            backupFileLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("SettingsFragment", "启动备份文件选择器失败", e)
            Toast.makeText(requireContext(), "启动备份失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startRestoreProcess() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }
        
        try {
            restoreFileLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("SettingsFragment", "启动恢复文件选择器失败", e)
            Toast.makeText(requireContext(), "启动恢复失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showClearDataDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清除数据")
            .setMessage("确定要清除所有单词和学习数据吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                settingsScope.launch {
                    try {
                        val success = DataBackupUtils.clearData(requireContext())
                        if (success) {
                            Toast.makeText(requireContext(), "数据已清除", Toast.LENGTH_SHORT).show()
                            // 重新加载设置
                            updateWordsPerDaySummary()
                            updateRecognitionTimeoutSummary()
                            updateSpeechRateSummary()
                            updateSpeechVolumeSummary()
                            updateThemeSummary()
                            updateOnlineTTSVoiceSummary()
                        } else {
                            Toast.makeText(requireContext(), "清除数据失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsFragment", "清除数据失败", e)
                        Toast.makeText(requireContext(), "清除数据失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:feedback@sonicwords.app")
            putExtra(Intent.EXTRA_SUBJECT, "SonicWords 反馈")
            putExtra(Intent.EXTRA_TEXT, "App版本: ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}\n\n反馈内容:\n")
        }
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "未找到邮件应用", Toast.LENGTH_SHORT).show()
        }
    }
} 