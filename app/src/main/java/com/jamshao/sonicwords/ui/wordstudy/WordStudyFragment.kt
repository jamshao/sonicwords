package com.jamshao.sonicwords.ui.wordstudy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
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
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jamshao.sonicwords.R
import com.jamshao.sonicwords.ui.wordstudy.WordCardAdapter
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.databinding.FragmentWordStudyBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import android.content.ActivityNotFoundException
import com.jamshao.sonicwords.utils.TTSHelper
import com.google.android.gms.common.GoogleApiAvailability

@AndroidEntryPoint
class WordStudyFragment : Fragment(), TextToSpeech.OnInitListener, CoroutineScope by CoroutineScope(Dispatchers.Main) {

    private var _binding: FragmentWordStudyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WordStudyViewModel by viewModels()
    private lateinit var adapter: WordCardAdapter
    private lateinit var ttsHelper: TTSHelper
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: android.content.Intent
    private var recognitionTimeout: Job? = null
    
    // 添加变量来跟踪是否在使用备用语音识别方法
    private var useGoogleCloudRecognition = false
    private var useAlternativeRecognition = false
    
    // 添加变量跟踪谷歌服务可用性
    private var isGooglePlayServicesAvailable = false
    
    // 标记是否正在手动录音
    private var isManualRecording = false

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
            initSpeechRecognizer()
        } else {
            Toast.makeText(requireContext(), R.string.voice_permission_required, Toast.LENGTH_LONG).show()
            binding.speechStatusText.text = "需要麦克风权限"
        }
    }

    // 添加谷歌云语音识别活动结果处理器
    private val cloudRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0].lowercase(Locale.US)
                // 处理识别结果
                handleRecognitionResult(spokenText)
            } else {
                Log.w("WordStudyFragment", "未能从Google云语音识别获取结果")
                binding.speechStatusText.text = "未能识别，请重试"
                startListening()
            }
        } else {
            Log.w("WordStudyFragment", "Google云语音识别被取消或失败")
            binding.speechStatusText.text = "识别取消，请重试"
            startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsHelper = TTSHelper(requireContext())
        ttsHelper.init()
        
        // 检查Google Play服务可用性
        checkGooglePlayServicesAvailability()
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
        
        // 添加语音识别方法切换按钮
        setupRecognitionMethodSwitch()
        
        // 设置发音按钮的点击事件
        binding.btnPronounce.setOnClickListener {
            val currentWord = viewModel.words.value?.getOrNull(viewModel.currentWordIndex.value ?: 0)
            if (currentWord != null) {
                speakCurrentLetter(currentWord)
                Toast.makeText(context, "正在朗读: ${currentWord.word}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "当前没有单词可朗读", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置录音按钮的触摸事件
        setupRecordButton()
        
        // 尝试设置语音识别，但即使失败也不影响其他功能
        try {
            setupSpeechRecognition()
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "设置语音识别失败，尝试备用方法: ${e.message}", e)
            // 尝试使用备用方法
            useAlternativeRecognition = true
            binding.speechStatusText.text = "正在使用备用语音识别方式"
        }
        
        // 朗读当前单词
        adapter.currentList.firstOrNull()?.let { speakCurrentLetter(it) }
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
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "UI组件检查失败，可能缺少某些视图: ${e.message}", e)
            Toast.makeText(context, "界面加载错误，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupViewPager() {
        adapter = WordCardAdapter(
            onEasyClick = { word ->
                speakCurrentLetter(word)
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
                adapter.currentList.getOrNull(position)?.let { speakCurrentLetter(it) }
                
                // 如果支持语音识别，才开始监听
                if (::speechRecognizer.isInitialized) {
                    startListening()
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
        val currentPosition = binding.viewpagerWordCards.currentItem
        if (currentPosition < adapter.itemCount - 1) {
            binding.viewpagerWordCards.currentItem = currentPosition + 1
        } else {
            Toast.makeText(requireContext(), "学习完成！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSpeechRecognition() {
        try {
            // 检查Fragment是否附加到Activity
            if (!isAdded || _binding == null) {
                Log.w("WordStudyFragment", "setupSpeechRecognition: Fragment未附加或binding为空")
                return
            }
            
            // 检查语音识别服务是否可用
            if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
                Log.w("WordStudyFragment", "设备不支持语音识别")
                Toast.makeText(requireContext(), "语音识别不可用，您将无法使用语音拼读功能", Toast.LENGTH_LONG).show()
                binding.speechStatusText.text = "语音识别不可用"
                binding.micAnimationIcon.setImageResource(android.R.drawable.ic_delete)
                
                // 如果Google Play服务可用，尝试使用Google Cloud语音识别
                if (isGooglePlayServicesAvailable) {
                    useGoogleCloudRecognition = true
                    binding.speechStatusText.text = "将使用Google云语音识别"
                } else {
                    // 显示提示，建议用户使用按钮选择
                    val noSpeechSnackbar = Snackbar.make(
                        binding.root, 
                        "此设备不支持语音识别，请直接点击按钮选择难度或使用录音按钮", 
                        Snackbar.LENGTH_LONG
                    )
                    noSpeechSnackbar.show()
                }
                return
            }
            
            // 创建并开始话筒动画
            try {
                val animation = android.view.animation.AnimationUtils.loadAnimation(
                    requireContext(), 
                    R.anim.mic_pulse
                )
                binding.micAnimationIcon.startAnimation(animation)
            } catch (e: Exception) {
                Log.e("WordStudyFragment", "启动麦克风动画失败: ${e.message}")
                // 动画失败不阻止后续流程
            }
            
            // 检查麦克风权限
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 已有权限，初始化语音识别
                    Log.d("WordStudyFragment", "已有麦克风权限，初始化语音识别")
                    initSpeechRecognizer()
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
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "setupSpeechRecognition异常: ${e.message}", e)
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
    
    private fun initSpeechRecognizer() {
        try {
            // 检查Fragment是否附加
            if (!isAdded) {
                Log.w("WordStudyFragment", "Fragment未附加，跳过语音识别器初始化")
                return
            }
            
            // 创建语音识别器
            Log.d("WordStudyFragment", "开始创建语音识别器")
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            
            // 配置语音识别意图
            speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, requireContext().packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            // 设置识别监听器
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isAdded || _binding == null) return
                    Log.d("WordStudyFragment", "语音识别准备就绪")
                    binding.speechStatusText.text = "请拼读单词"
                    recognitionTimeout?.cancel()
                }
                
                override fun onBeginningOfSpeech() {
                    if (!isAdded || _binding == null) return
                    Log.d("WordStudyFragment", "开始语音输入")
                    binding.speechStatusText.text = "正在聆听..."
                    recognitionTimeout?.cancel()
                }
                
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    if (!isAdded || _binding == null) return
                    Log.d("WordStudyFragment", "语音输入结束")
                    binding.speechStatusText.text = "识别中..."
                }
                
                override fun onError(error: Int) {
                    if (!isAdded || _binding == null) return
                    
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "未能匹配语音"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                        else -> "未知错误"
                    }
                    
                    Log.w("WordStudyFragment", "语音识别错误: $errorMessage (错误码: $error)")
                    
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        binding.speechStatusText.text = "需要麦克风权限"
                        Toast.makeText(context, R.string.voice_permission_required, Toast.LENGTH_LONG).show()
                        showPermissionRationaleDialog()
                        return
                    }
                    
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && 
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        binding.speechStatusText.text = "语音识别错误: $errorMessage"
                        Toast.makeText(context, "语音识别错误: $errorMessage", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.speechStatusText.text = "请拼读单词"
                    }
                    
                    launch { 
                        try {
                            delay(1000)
                            if (isAdded) startListening()
                        } catch (e: Exception) {
                            Log.e("WordStudyFragment", "重启语音识别失败: ${e.message}")
                        }
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    if (!isAdded || _binding == null) return
                    
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.d("WordStudyFragment", "语音识别结果: $matches")
                    
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0].lowercase(Locale.US)
                        binding.tvSpokenLetter.text = "您说: $spokenText"
                        
                        val currentWord = viewModel.words.value?.getOrNull(viewModel.currentWordIndex.value ?: -1)
                        if (currentWord != null && spokenText == currentWord.word.lowercase(Locale.US)) {
                            Log.d("WordStudyFragment", "单词拼读正确: $spokenText")
                            binding.speechStatusText.text = "发音正确!"
                            viewModel.markWordAsKnown(currentWord)
                            goToNextWord()
                        } else {
                            Log.d("WordStudyFragment", "单词拼读不匹配: $spokenText vs ${currentWord?.word}")
                            binding.speechStatusText.text = "发音不匹配，请重试"
                            Toast.makeText(requireContext(), "发音不匹配: $spokenText", Toast.LENGTH_SHORT).show()
                            if (currentWord != null) {
                                speakCurrentLetter(currentWord)
                            }
                            startListening()
                        }
                    } else {
                        Log.w("WordStudyFragment", "未能识别语音内容")
                        binding.speechStatusText.text = "未能识别，请重试"
                        startListening()
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            // 初始化成功后启动语音识别
            Log.d("WordStudyFragment", "语音识别器初始化成功，开始监听")
            startListening()
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "初始化语音识别失败: ${e.message}", e)
            if (isAdded && _binding != null) {
                Toast.makeText(requireContext(), "初始化语音识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.speechStatusText.text = "语音识别初始化失败"
            }
        }
    }

    private fun startListening(isManual: Boolean = false) {
        try {
            // 根据当前设置选择合适的语音识别方法
            when {
                useGoogleCloudRecognition -> {
                    // 使用Google云语音识别（通过Activity）
                    startGoogleCloudRecognition()
                    return
                }
                useAlternativeRecognition -> {
                    // 使用离线语音识别
                    startOfflineRecognition()
                    return
                }
                // 标准SpeechRecognizer方法
                else -> {
                    // 检查语音识别器是否已初始化
                    if (!::speechRecognizer.isInitialized) {
                        Log.d("WordStudyFragment", "语音识别器未初始化，尝试重新初始化")
                        setupSpeechRecognition()
                        if (!::speechRecognizer.isInitialized) {
                            // 根据Google Play服务可用性选择备用方法
                            if (isGooglePlayServicesAvailable) {
                                useGoogleCloudRecognition = true
                                startGoogleCloudRecognition()
                            } else {
                                useAlternativeRecognition = true
                                startOfflineRecognition()
                            }
                            return
                        }
                    }
                    
                    // 检查设备是否支持语音识别
                    if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
                        Log.w("WordStudyFragment", "设备不支持语音识别，尝试使用备用方法")
                        // 根据Google Play服务可用性选择备用方法
                        if (isGooglePlayServicesAvailable) {
                            useGoogleCloudRecognition = true
                            startGoogleCloudRecognition()
                        } else {
                            useAlternativeRecognition = true
                            startOfflineRecognition()
                        }
                        return
                    }
                    
                    // 继续使用标准SpeechRecognizer的方法
                    if (!isManual) {
                        // 非手动模式下设置超时
                        recognitionTimeout?.cancel()
                        recognitionTimeout = launch { 
                            try {
                                delay(5000) 
                                if (isAdded && ::speechRecognizer.isInitialized && !isManualRecording) {
                                    Log.d("WordStudyFragment", "语音识别超时，重新开始")
                                    speechRecognizer.stopListening()
                                    startListening()
                                }
                            } catch (e: Exception) {
                                Log.e("WordStudyFragment", "处理语音识别超时异常: ${e.message}")
                            }
                        }
                    }
                    
                    // 检查视图和Fragment状态
                    if (!isAdded || _binding == null) {
                        Log.w("WordStudyFragment", "Fragment未附加或binding为空，跳过语音识别")
                        return
                    }
                    
                    // 检查麦克风权限
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED) {
                        Log.w("WordStudyFragment", "缺少麦克风权限，无法启动语音识别")
                        binding.speechStatusText.text = "需要麦克风权限"
                        setupSpeechRecognition()
                        return
                    }
                    
                    try {
                        if (isManual) {
                            binding.speechStatusText.text = "请说出单词..."
                        } else {
                            binding.speechStatusText.text = "请拼读单词"
                        }
                        Log.d("WordStudyFragment", "开始语音识别")
                        speechRecognizer.startListening(speechRecognizerIntent)
                    } catch (e: Exception) {
                        Log.e("WordStudyFragment", "启动语音识别失败，尝试备用方法: ${e.message}", e)
                        // 如果标准方法失败，根据Google Play服务可用性选择备用方法
                        if (isGooglePlayServicesAvailable) {
                            useGoogleCloudRecognition = true
                            startGoogleCloudRecognition()
                        } else {
                            useAlternativeRecognition = true
                            startOfflineRecognition()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "startListening总体异常，尝试备用方法: ${e.message}", e)
            // 出现异常时根据Google Play服务可用性选择备用方法
            if (isGooglePlayServicesAvailable) {
                useGoogleCloudRecognition = true
                startGoogleCloudRecognition()
            } else {
                useAlternativeRecognition = true
                startOfflineRecognition()
            }
        }
    }
    
    // Google云语音识别方法（通过启动Activity）
    private fun startGoogleCloudRecognition() {
        try {
            binding.speechStatusText.text = "启动Google云语音识别..."
            
            // 创建Intent来启动Google语音识别活动
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请拼读单词")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            try {
                // 启动Google语音识别活动
                cloudRecognitionLauncher.launch(intent)
                binding.speechStatusText.text = "请对着麦克风拼读..."
            } catch (e: ActivityNotFoundException) {
                // 设备上没有语音识别活动
                Log.e("WordStudyFragment", "未找到语音识别活动: ${e.message}", e)
                binding.speechStatusText.text = "设备不支持Google云语音识别"
                Toast.makeText(context, "此设备不支持Google云语音识别", Toast.LENGTH_SHORT).show()
                
                // 尝试离线识别作为备选
                useGoogleCloudRecognition = false
                useAlternativeRecognition = true
                startOfflineRecognition()
            }
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "启动Google云语音识别异常: ${e.message}", e)
            binding.speechStatusText.text = "语音识别出错，请手动选择难度"
        }
    }
    
    // 离线语音识别方法
    private fun startOfflineRecognition() {
        try {
            binding.speechStatusText.text = "已切换至离线识别模式"
            
            // 这里使用简化的识别方式 - 直接显示提示让用户点击按钮
            val currentWord = viewModel.words.value?.getOrNull(viewModel.currentWordIndex.value ?: 0)
            if (currentWord != null) {
                binding.tvSpokenLetter.text = "请拼读: ${currentWord.word}"
                
                // 显示Snackbar提示
                val snackbar = Snackbar.make(
                    binding.root,
                    "语音识别不可用，请拼读单词后点击熟悉度按钮",
                    Snackbar.LENGTH_LONG
                )
                snackbar.show()
                
                // 在此模式下，用户需要自己判断难度并点击对应按钮
                // 无需额外操作，因为按钮已经在UI中并有相应的点击处理
            }
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "离线识别模式异常: ${e.message}", e)
            binding.speechStatusText.text = "识别不可用，请手动选择熟悉度"
        }
    }
    
    // 处理语音识别结果的通用方法
    private fun handleRecognitionResult(spokenText: String) {
        if (!isAdded || _binding == null) return
        
        binding.tvSpokenLetter.text = "您说: $spokenText"
        
        val currentWord = viewModel.words.value?.getOrNull(viewModel.currentWordIndex.value ?: -1)
        if (currentWord != null && spokenText == currentWord.word.lowercase(Locale.US)) {
            Log.d("WordStudyFragment", "单词拼读正确: $spokenText")
            binding.speechStatusText.text = "发音正确!"
            viewModel.markWordAsKnown(currentWord)
            goToNextWord()
        } else {
            Log.d("WordStudyFragment", "单词拼读不匹配: $spokenText vs ${currentWord?.word}")
            binding.speechStatusText.text = "发音不匹配，请重试"
            Toast.makeText(requireContext(), "发音不匹配: $spokenText", Toast.LENGTH_SHORT).show()
            if (currentWord != null) {
                speakCurrentLetter(currentWord)
            }
            startListening()
        }
    }

    private fun speakCurrentLetter(word: Word?) {
        word?.word?.let {
            ttsHelper.speak(it)
        }
    }

    override fun onInit(status: Int) {
        // 已不再需要此方法，但由于实现了接口，保留一个空实现
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        recognitionTimeout?.cancel()
        recognitionTimeout = null
        
        try {
            binding.micAnimationIcon.clearAnimation()
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "停止动画失败", e)
        }
        
        if (::ttsHelper.isInitialized) {
            try {
                ttsHelper.shutdown()
            } catch (e: Exception) {
                Log.e("WordStudyFragment", "释放TTS资源失败", e)
            }
        }
        
        if (::speechRecognizer.isInitialized) {
            try {
                speechRecognizer.destroy()
            } catch (e: Exception) {
                Log.e("WordStudyFragment", "销毁语音识别器异常: ${e.message}")
            }
        }
        
        try {
            viewModel.updateStudyTime()
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "更新学习时间失败", e)
        }
        
        _binding = null
    }

    // 设置语音识别方法切换按钮
    private fun setupRecognitionMethodSwitch() {
        binding.btnSwitchRecognition.setOnClickListener {
            when {
                !useAlternativeRecognition && !useGoogleCloudRecognition -> {
                    // 根据Google Play服务可用性决定切换到哪种模式
                    if (isGooglePlayServicesAvailable) {
                        // 从默认方式切换到Google云语音识别
                        useGoogleCloudRecognition = true
                        useAlternativeRecognition = false
                        Toast.makeText(context, "已切换至Google云语音识别", Toast.LENGTH_SHORT).show()
                        binding.btnSwitchRecognition.text = "切换至离线识别"
                    } else {
                        // 直接切换到离线识别
                        useGoogleCloudRecognition = false
                        useAlternativeRecognition = true
                        Toast.makeText(context, "已切换至离线识别", Toast.LENGTH_SHORT).show()
                        binding.btnSwitchRecognition.text = "切换至默认识别"
                    }
                }
                useGoogleCloudRecognition -> {
                    // 从Google云语音识别切换到离线识别
                    useGoogleCloudRecognition = false
                    useAlternativeRecognition = true
                    Toast.makeText(context, "已切换至离线识别", Toast.LENGTH_SHORT).show()
                    binding.btnSwitchRecognition.text = "切换至默认识别"
                }
                else -> {
                    // 从离线识别切换回默认方式
                    useGoogleCloudRecognition = false
                    useAlternativeRecognition = false
                    Toast.makeText(context, "已切换至默认识别", Toast.LENGTH_SHORT).show()
                    binding.btnSwitchRecognition.text = "切换识别方式"
                    // 重新初始化默认识别
                    try {
                        setupSpeechRecognition()
                    } catch (e: Exception) {
                        Log.e("WordStudyFragment", "重新设置默认语音识别失败: ${e.message}", e)
                    }
                }
            }
            // 立即使用新的识别方式
            startListening()
        }
    }

    /**
     * 检查Google Play服务可用性
     */
    private fun checkGooglePlayServicesAvailability() {
        try {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(requireContext())
            isGooglePlayServicesAvailable = (resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS)
            
            Log.d("WordStudyFragment", "Google Play服务可用性: $isGooglePlayServicesAvailable")
        } catch (e: Exception) {
            Log.e("WordStudyFragment", "检查Google Play服务可用性失败: ${e.message}", e)
            isGooglePlayServicesAvailable = false
        }
    }
    
    /**
     * 设置录音按钮的触摸事件
     */
    private fun setupRecordButton() {
        binding.btnRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下按钮时开始录音
                    isManualRecording = true
                    binding.speechStatusText.text = "请说出单词..."
                    startListening(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 松开按钮时停止录音
                    isManualRecording = false
                    if (::speechRecognizer.isInitialized) {
                        try {
                            speechRecognizer.stopListening()
                        } catch (e: Exception) {
                            Log.e("WordStudyFragment", "停止语音识别失败: ${e.message}", e)
                        }
                    }
                }
            }
            true
        }
    }
}