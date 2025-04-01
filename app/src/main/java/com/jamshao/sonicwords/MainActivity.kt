package com.jamshao.sonicwords

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.jamshao.sonicwords.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 加载默认设置
        initDefaultSettings()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // 设置底部导航
        binding.bottomNavigation.setupWithNavController(navController)
        
        // 确保底部导航与导航控制器同步
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)
    }
    
    /**
     * 初始化默认设置项
     */
    private fun initDefaultSettings() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPreferences.edit()
        
        // 如果是首次运行应用，设置默认值
        if (!sharedPreferences.contains("is_first_run")) {
            // 设置默认学习单词数量
            if (!sharedPreferences.contains("words_per_day")) {
                editor.putInt("words_per_day", 20)
            }
            
            // 设置默认语音识别超时
            if (!sharedPreferences.contains("recognition_timeout")) {
                editor.putInt("recognition_timeout", 5)
            }
            
            // 设置默认语音速度
            if (!sharedPreferences.contains("speech_rate")) {
                editor.putInt("speech_rate", 50)
            }
            
            // 设置默认语音音量
            if (!sharedPreferences.contains("speech_volume")) {
                editor.putInt("speech_volume", 100)
            }
            
            // 设置默认主题
            if (!sharedPreferences.contains("theme_mode")) {
                editor.putString("theme_mode", "system")
            }
            
            // 标记应用已初始化
            editor.putBoolean("is_first_run", false)
            editor.apply()
        }
    }
}