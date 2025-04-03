package com.jamshao.sonicwords.ui.wordstudy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jamshao.sonicwords.R
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.databinding.FragmentWordStudyBinding
import com.jamshao.sonicwords.service.VoskRecognitionService
import com.jamshao.sonicwords.utils.TTSHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WordStudyFragment : Fragment(), TextToSpeech.OnInitListener, CoroutineScope by CoroutineScope(Dispatchers.Main) {

    private val TAG = "WordStudyFragment"

    private var _binding: FragmentWordStudyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WordStudyViewModel by viewModels()
    private lateinit var adapter: WordCardAdapter
    private lateinit var ttsHelper: TTSHelper
    private var recognitionTimeout: Job? = null
    
    // 只保留Vosk识别
    private var isListening = false
    
    @Inject
    lateinit var voskRecognitionService: VoskRecognitionService
    
    // 定义Vosk识别监听器
    private val voskRecognitionListener = object : VoskRecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (!isAdded || _binding == null) return
            
            if (hypothesis != null) {
                processVoskPartialResult(hypothesis)
            }
        }

        override fun onResult(hypothesis: String?) {
            if (!isAdded || _binding == null) return
            
            if (hypothesis != null) {
                processVoskResult(hypothesis)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            // 最终结果与onResult相同处理逻辑
        }

        override fun onError(exception: Exception?) {
            if (!isAdded || _binding == null) return
            
            Log.e(TAG, "Vosk识别错误: ${exception?.message}", exception)
            binding.speechStatusText.text = "识别出错，请重试"
            isListening = false
        }

        override fun onTimeout() {
            if (!isAdded || _binding == null) return
            
            Log.w(TAG, "Vosk识别超时")
            binding.speechStatusText.text = "识别超时，请重试"
            isListening = false
        }
    }
    
    private fun processVoskPartialResult(hypothesis: String) {
        try {
            val result = JSONObject(hypothesis)
            val partial = result.optString("partial")
            if (!partial.isNullOrEmpty()) {
                binding.tvSpokenLetter.text = "正在识别: $partial"
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析部分结果失败: ${e.message}", e)
        }
    }
    
    private fun processVoskResult(hypothesis: String) {
        try {
            val result = JSONObject(hypothesis)
            val text = result.optString("text").trim().lowercase(Locale.US)
            
            if (!text.isNullOrEmpty()) {
                binding.tvSpokenLetter.text = "您说: $text"
                
                // 获取当前单词
                val currentWord = viewModel.getCurrentWord()
                
                if (currentWord != null) {
                    // 判断用户是否正确拼写了整个单词
                    if (viewModel.checkWholeWord(text)) {
                        // 单词拼写正确
                        binding.speechStatusText.text = "拼写正确!"
                        Toast.makeText(requireContext(), "单词 ${currentWord.word} 拼写正确!", Toast.LENGTH_SHORT).show()
                        
                        // 延迟后切换到下一个单词
                        Handler(Looper.getMainLooper()).postDelayed({
                            goToNextWord()
                        }, 1500)
                    } else {
                        // 单词拼写错误
                        binding.speechStatusText.text = "拼写错误! 请再试一次"
                        
                        // 获取错误次数
                        val errorCount = viewModel.currentWordErrorCount.value ?: 0
                        
                        // 如果错误次数达到3次，显示正确的拼写
                        if (errorCount >= 3) {
                            val correctSpelling = viewModel.getCorrectSpelling()
                            if (correctSpelling != null) {
                                binding.speechStatusText.text = "正确的拼写是: $correctSpelling"
                                ttsHelper.speak("正确的拼写是 ${correctSpelling}")
                                Toast.makeText(requireContext(), "单词已标记为困难", Toast.LENGTH_SHORT).show()
                            }
                            
                            // 延迟后切换到下一个单词
                            Handler(Looper.getMainLooper()).postDelayed({
                                goToNextWord()
                            }, 3000)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析结果失败: ${e.message}", e)
            binding.speechStatusText.text = "请重新拼读单词"
        }
    }

    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0].lowercase(Locale.US)
                val currentWord = viewModel.words.value?.getOrNull(viewModel.currentWordIndex.value ?: 0)
                if (currentWord != null && spokenText == currentWord.word.lowercase(Locale.US)) {
                    viewModel.markWordAsKnown(currentWord)
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限已授予，不需要再次初始化语音识别
        } else {
            Toast.makeText(requireContext(), R.string.voice_permission_required, Toast.LENGTH_LONG).show()
            binding.speechStatusText.text = "需要麦克风权限"
        }
    }

    // 添加语音识别活动结果处理器
    private val cloudRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0].lowercase(Locale.US)
                // 处理语音识别结果
                processVoskResult(JSONObject().put("text", spokenText).toString())
            } else {
                Log.w("WordStudyFragment", "未能获取语音识别结果")
                binding.speechStatusText.text = "未能识别，请重试"
            }
        } else {
            Log.w("WordStudyFragment", "语音识别被取消或失败")
            binding.speechStatusText.text = "识别取消，请重试"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsHelper = TTSHelper(requireContext())
        ttsHelper.init()
        
        // 初始化Vosk
        initializeVosk()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWordStudyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化所有UI组件，确保组件都存在于布局中
        ensureUIComponentsExist()
        setupViewPager()
        setupObservers()
        
        // 设置发音按钮的点击事件
        setupClickListeners()
        
        // 观察单词列表以配置Vosk词汇表
        viewModel.words.observe(viewLifecycleOwner) { words ->
            if (words.isNotEmpty()) {
                // 配置Vosk为单词识别模式而非字母模式
                configureVoskForWords(words)
                
                adapter.submitList(words)
                updateProgressUI(viewModel.currentWordIndex.value ?: 0, words.size)
                binding.tvEmptyState.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
                
                // 获取当前单词并设置字母提示
                val currentWord = viewModel.getCurrentWord()
                if (currentWord != null) {
                    speakCurrentWord(currentWord)
                }
            }
        }
        
        // 观察当前单词索引变化
        viewModel.currentWordIndex.observe(viewLifecycleOwner, Observer { index ->
            binding.viewpagerWordCards.currentItem = index
            updateProgressUI(index, adapter.itemCount)
            
            // 获取当前单词并重置拼写状态
            val currentWord = viewModel.getCurrentWord()
            if (currentWord != null) {
                viewModel.resetSpelling()
                // 确保停止之前的TTS播放，避免重复
                ttsHelper.stop()
                speakCurrentWord(currentWord)
            }
        })

        // 观察拼写状态变化
        viewModel.spellingState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                WordStudyViewModel.SpellingState.CorrectLetter -> {
                    // 字母拼写正确的视觉反馈
                    binding.tvSpokenLetter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                }
                WordStudyViewModel.SpellingState.WrongLetter -> {
                    // 字母拼写错误的视觉反馈
                    binding.tvSpokenLetter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                }
                WordStudyViewModel.SpellingState.CompleteWord -> {
                    // 单词完成的视觉反馈
                    binding.tvSpokenLetter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                    binding.tvSpokenLetter.text = "单词拼写完成!"
                }
                else -> {
                    // 重置文本颜色
                    binding.tvSpokenLetter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                }
            }
        })

        viewModel.todayLearnedCount.observe(viewLifecycleOwner, Observer { count ->
            binding.tvLearnedCount.text = getString(R.string.today_learned_format, count)
        })
        
        viewModel.todayReviewCount.observe(viewLifecycleOwner, Observer { count ->
            binding.tvReviewCount.text = "今日已复习: $count"
        })
        
        viewModel.todayStudyTime.observe(viewLifecycleOwner, Observer { timeString ->
            binding.tvStudyTime.text = getString(R.string.today_study_time_format, timeString)
        })

        // 观察学习模式，更新UI
        viewModel.currentMode.observe(viewLifecycleOwner, Observer { mode ->
            if (mode != null) {
                when (mode) {
                    WordStudyViewModel.StudyMode.NEW_WORDS -> {
                        binding.btnToggleMode.text = "切换到复习模式"
                        binding.tvModeIndicator.text = "新词学习模式"
                    }
                    WordStudyViewModel.StudyMode.REVIEW -> {
                        binding.btnToggleMode.text = "切换到新词模式"
                        binding.tvModeIndicator.text = "单词复习模式"
                    }
                }
            }
        })
    }
    
    /**
     * 确保UI组件存在，避免空引用
     */
    private fun ensureUIComponentsExist() {
        try {
            // 检查视图是否存在，避免NPE
            binding.viewpagerWordCards
            binding.progressBar
            binding.tvProgress
            binding.tvEmptyState
            binding.tvLearnedCount
            binding.tvReviewCount
            binding.tvStudyTime
            binding.speechStatusText
            binding.micAnimationIcon
            binding.tvSpokenLetter
            binding.tvCurrentLetter
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "UI组件检查失败，可能缺少某些视图: ${e.message}", e)
            Toast.makeText(context, "界面加载错误，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupViewPager() {
        adapter = WordCardAdapter(
            onEasyClick = { word ->
                viewModel.markWordAsEasy(word)
                goToNextWord()
            },
            onMediumClick = { word ->
                viewModel.markWordAsMedium(word)
                goToNextWord()
            },
            onHardClick = { word ->
                viewModel.markWordAsHard(word)
                goToNextWord()
            }
        )
        binding.viewpagerWordCards.adapter = adapter

        binding.viewpagerWordCards.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val currentWord = viewModel.getCurrentWord()
                if (currentWord != null) {
                    viewModel.resetSpelling()
                    // 确保停止之前的TTS播放，避免重复
                    ttsHelper.stop()
                    speakCurrentWord(currentWord)
                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.words.observe(viewLifecycleOwner, Observer { words ->
            adapter.submitList(words)
            updateProgressUI(viewModel.currentWordIndex.value ?: 0, words.size)
            binding.tvEmptyState.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
        })

        viewModel.currentWordIndex.observe(viewLifecycleOwner, Observer { index ->
            binding.viewpagerWordCards.currentItem = index
            updateProgressUI(index, adapter.itemCount)
        })

        viewModel.todayLearnedCount.observe(viewLifecycleOwner, Observer { count ->
            binding.tvLearnedCount.text = getString(R.string.today_learned_format, count)
        })
        
        viewModel.todayReviewCount.observe(viewLifecycleOwner, Observer { count ->
            binding.tvReviewCount.text = "今日已复习: $count"
        })
        
        viewModel.todayStudyTime.observe(viewLifecycleOwner, Observer { timeString ->
            binding.tvStudyTime.text = getString(R.string.today_study_time_format, timeString)
        })
    }
    
    private fun updateProgressUI(currentIndex: Int, totalWords: Int) {
        if (totalWords > 0) {
            val progress = ((currentIndex + 1).toFloat() / totalWords * 100).toInt()
            binding.progressBar.progress = progress
            binding.tvProgress.text = "${currentIndex + 1}/$totalWords"
        } else {
            binding.progressBar.progress = 0
            binding.tvProgress.text = "0/0"
        }
    }

    private fun goToNextWord() {
        viewModel.nextWord()
        // 重置UI状态
        resetUIState()
    }
    
    private fun goToPreviousWord() {
        viewModel.previousWord()
        // 重置UI状态
        resetUIState()
    }
    
    private fun resetUIState() {
        // 重置错误状态显示
        binding.speechStatusText.text = getString(R.string.please_spell_word)
        binding.tvSpokenLetter.text = ""
        
        // 重置键盘输入框
        binding.etLetterInput.setText("")
        
        // 停止当前的语音识别
        stopListening()
        
        // 获取当前单词并重新播放，但先停止当前TTS播放
        val currentWord = viewModel.getCurrentWord()
        if (currentWord != null) {
            ttsHelper.stop()
            speakCurrentWord(currentWord)
        }
    }

    private fun setupPermissions() {
        // 检查麦克风权限
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // 已有权限，不需要任何操作
                Log.d("WordStudyFragment", "已有麦克风权限")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // 需要解释为什么需要权限
                Log.d("WordStudyFragment", "需要向用户解释麦克风权限用途")
                showPermissionRationaleDialog()
            }
            else -> {
                // 首次请求权限
                Log.d("WordStudyFragment", "首次请求麦克风权限")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.microphone_permission_title)
            .setMessage(R.string.voice_permission_required)
            .setPositiveButton(R.string.authorize) { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startListening() {
        try {
            // 检查麦克风权限
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }

            // 使用Vosk离线识别
            if (voskRecognitionService.isReady && !isListening) {
                binding.speechStatusText.text = "请拼读单词"
                voskRecognitionService.startListening(voskRecognitionListener)
                isListening = true
                
                // 创建并开始话筒动画
                try {
                    val animation = android.view.animation.AnimationUtils.loadAnimation(
                        requireContext(), 
                        R.anim.mic_pulse
                    )
                    binding.micAnimationIcon.startAnimation(animation)
                } catch (e: Exception) {
                    Log.e("WordStudyFragment", "启动麦克风动画失败: ${e.message}")
                }
            } else if (!voskRecognitionService.isReady) {
                binding.speechStatusText.text = "语音识别未准备好"
                Toast.makeText(context, "语音识别未准备好，请稍后再试", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败: ${e.message}", e)
            binding.speechStatusText.text = "语音识别启动失败"
            Toast.makeText(context, "语音识别启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            isListening = false
        }
    }
    
    private fun stopListening() {
        try {
            if (isListening) {
                voskRecognitionService.stopListening()
                isListening = false
                binding.speechStatusText.text = "识别已停止"
                
                // 停止麦克风动画
                binding.micAnimationIcon.clearAnimation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止语音识别失败: ${e.message}", e)
        }
    }

    private fun speakCurrentWord(word: Word?) {
        word?.let { w ->
            // 确保先停止之前的播放，避免重复
            ttsHelper.stop()
            
            // 更新UI显示中文释义
            binding.tvCurrentMeaning.text = w.chineseMeaning
            
            // 隐藏字母提示，因为现在是整个单词拼写
            binding.tvCurrentLetter.text = "请拼写与此意思对应的英文单词"
            
            // 构建要朗读的文本：英文单词+中文释义
            val textToSpeak = if (w.chineseMeaning.isNullOrEmpty()) {
                w.word
            } else {
                "${w.word}, ${w.chineseMeaning}"
            }
            
            // 朗读整个单词和中文含义
            ttsHelper.speak(textToSpeak)
        }
    }

    override fun onInit(status: Int) {
        // TextToSpeech初始化回调
        // 目前使用TTSHelper处理，不需要在这里实现
    }

    override fun onPause() {
        super.onPause()
        
        // 停止当前的语音识别和TTS
        stopListening()
        if (::ttsHelper.isInitialized) {
            try {
                ttsHelper.stop()
            } catch (e: Exception) {
                Log.e(TAG, "停止TTS失败: ${e.message}", e)
            }
        }
        
        // 更新学习时间
        try {
            viewModel.updateStudyTime()
        } catch (e: Exception) {
            Log.e(TAG, "更新学习时间失败: ${e.message}", e)
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // 停止当前的语音识别和TTS
        stopListening()
        if (::ttsHelper.isInitialized) {
            try {
                ttsHelper.stop()
            } catch (e: Exception) {
                Log.e(TAG, "停止TTS失败: ${e.message}", e)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        recognitionTimeout?.cancel()
        recognitionTimeout = null
        
        try {
            binding.micAnimationIcon.clearAnimation()
        } catch (e: Exception) {
            Log.e(TAG, "停止动画失败", e)
        }
        
        if (::ttsHelper.isInitialized) {
            try {
                // 首先停止TTS，然后关闭资源
                ttsHelper.stop()
                ttsHelper.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "释放TTS资源失败", e)
            }
        }
        
        try {
            viewModel.updateStudyTime()
        } catch (e: Exception) {
            Log.e(TAG, "更新学习时间失败", e)
        }
        
        // 关闭Vosk资源
        try {
            voskRecognitionService.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "停止Vosk识别失败: ${e.message}", e)
        }
        
        _binding = null
    }

    /**
     * 设置录音按钮的触摸事件
     */
    private fun setupRecordButton() {
        binding.btnRecord.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下时提供触觉反馈
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    // 按下按钮时开始录音
                    startListening()
                    // 视觉反馈 - 改变按钮状态
                    v.isPressed = true
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 松开按钮时停止录音
                    stopListening()
                    // 视觉反馈 - 恢复按钮状态
                    v.isPressed = false
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            true
        }
    }

    private fun setupNavigationButtons() {
        binding.btnPrevious.setOnClickListener { v ->
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 视觉反馈
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            
            goToPreviousWord()
            
            // 重置键盘输入框
            binding.etLetterInput.setText("")
        }
        
        binding.btnNext.setOnClickListener { v ->
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 视觉反馈
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            
            goToNextWord()
            
            // 重置键盘输入框
            binding.etLetterInput.setText("")
        }
    }

    // 添加初始化Vosk的方法
    private fun initializeVosk() {
        lifecycleScope.launch {
            try {
                val success = voskRecognitionService.initializeRecognizer()
                
                if (success) {
                    Log.d(TAG, "Vosk初始化成功")
                    
                    // 配置Vosk为单词识别模式
                    configureVoskForWords(viewModel.words.value ?: emptyList())
                    
                    // 初始化成功后，设置当前单词和字母
                    val currentWord = viewModel.getCurrentWord()
                    if (currentWord != null) {
                        speakCurrentWord(currentWord)
                    }
                } else {
                    Log.w(TAG, "Vosk初始化失败，请检查设备配置")
                    binding.speechStatusText.text = "语音识别不可用"
                    Toast.makeText(context, "语音识别初始化失败，请检查设备配置", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vosk初始化异常: ${e.message}", e)
                binding.speechStatusText.text = "语音识别初始化异常"
                Toast.makeText(context, "语音识别初始化异常: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 配置Vosk为单词识别模式
    private fun configureVoskForWords(words: List<Word>) {
        // 提取所有单词作为识别词汇
        val vocabulary = words.map { it.word.lowercase(Locale.US) }
        voskRecognitionService.configureVocabulary(vocabulary)
    }

    private fun setupClickListeners() {
        // 发音按钮
        binding.btnPronounce.setOnClickListener { v ->
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 视觉反馈
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            
            // 获取当前选中的单词并朗读
            val currentWord = viewModel.getCurrentWord()
            if (currentWord != null) {
                // 停止之前的TTS播放，避免重复
                ttsHelper.stop()
                speakCurrentWord(currentWord)
            } else {
                Toast.makeText(context, "当前没有单词可朗读", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置录音按钮的触摸事件
        setupRecordButton()
        
        // 设置字母输入按钮
        binding.btnSubmitLetter.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 获取用户输入的单词
            val inputWord = binding.etLetterInput.text.toString().trim()
            
            if (inputWord.isNotEmpty()) {
                processKeyboardInput(inputWord)
                // 清空输入框
                binding.etLetterInput.setText("")
            } else {
                Toast.makeText(context, "请输入单词", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置模式切换按钮
        binding.btnToggleMode.setOnClickListener { v ->
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 切换学习模式
            viewModel.toggleStudyMode()
        }
        
        // 设置上一个和下一个按钮的点击事件
        setupNavigationButtons()
    }

    /**
     * 处理键盘输入的字母
     */
    private fun processKeyboardInput(inputWord: String) {
        try {
            // 显示用户输入的单词
            binding.tvSpokenLetter.text = "您输入: $inputWord"
            
            // 获取当前单词
            val currentWord = viewModel.getCurrentWord()
            
            if (currentWord != null) {
                // 判断用户是否正确拼写了当前单词
                if (viewModel.checkWholeWord(inputWord)) {
                    // 单词拼写正确
                    binding.speechStatusText.text = "拼写正确!"
                    Toast.makeText(requireContext(), "单词 ${currentWord.word} 拼写正确!", Toast.LENGTH_SHORT).show()
                    
                    // 延迟后切换到下一个单词
                    Handler(Looper.getMainLooper()).postDelayed({
                        goToNextWord()
                    }, 1500)
                } else {
                    // 单词拼写错误
                    binding.speechStatusText.text = "拼写错误! 请再试一次"
                    
                    // 获取错误次数
                    val errorCount = viewModel.currentWordErrorCount.value ?: 0
                    
                    // 如果错误次数达到3次，显示正确的拼写
                    if (errorCount >= 3) {
                        val correctSpelling = viewModel.getCorrectSpelling()
                        if (correctSpelling != null) {
                            binding.speechStatusText.text = "正确的拼写是: $correctSpelling"
                            Toast.makeText(requireContext(), "单词已标记为困难", Toast.LENGTH_SHORT).show()
                        }
                        
                        // 延迟后切换到下一个单词
                        Handler(Looper.getMainLooper()).postDelayed({
                            goToNextWord()
                        }, 3000)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理键盘输入失败: ${e.message}", e)
            binding.speechStatusText.text = "请重新输入单词"
        }
    }
}