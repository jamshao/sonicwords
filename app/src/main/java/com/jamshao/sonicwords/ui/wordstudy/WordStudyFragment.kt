package com.jamshao.sonicwords.ui.wordstudy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
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
import org.json.JSONObject
import com.jamshao.sonicwords.service.VoskRecognitionService
import javax.inject.Inject
import android.speech.RecognitionListener as AndroidRecognitionListener
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import android.annotation.SuppressLint

@AndroidEntryPoint
class WordStudyFragment : Fragment(), TextToSpeech.OnInitListener, CoroutineScope by CoroutineScope(Dispatchers.Main) {

    private val TAG = "WordStudyFragment"

    private var _binding: FragmentWordStudyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WordStudyViewModel by viewModels()
    private lateinit var adapter: WordCardAdapter
    private lateinit var ttsHelper: TTSHelper
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: android.content.Intent
    private var recognitionTimeout: Job? = null
    
    // 添加使用标志
    private var useVoskRecognition = true
    private var useGoogleCloudRecognition = false
    private var useAlternativeRecognition = false
    private var isManualRecording = false
    
    // 添加谷歌服务可用性标记
    private var isGooglePlayServicesAvailable = false
    
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
            
            // 如果Vosk出错，尝试使用其他识别方式
            useAlternativeRecognition = true
            startListening()
        }

        override fun onTimeout() {
            if (!isAdded || _binding == null) return
            
            Log.w(TAG, "Vosk识别超时")
            binding.speechStatusText.text = "识别超时，请重试"
            startListening()
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
            val text = result.optString("text")
            val cleanText = text.trim().lowercase(Locale.US)
            
            if (!cleanText.isNullOrEmpty()) {
                binding.tvSpokenLetter.text = "您说: $cleanText"
                
                val currentWord = viewModel.words.value?.getOrNull(viewModel.currentWordIndex.value ?: -1)
                if (currentWord != null && cleanText == currentWord.word.lowercase(Locale.US)) {
                    Log.d(TAG, "Vosk识别单词正确: $cleanText")
                    binding.speechStatusText.text = "发音正确!"
                    viewModel.markWordAsKnown(currentWord)
                    goToNextWord()
                } else {
                    Log.d(TAG, "Vosk识别不匹配: '$cleanText' vs '${currentWord?.word}'")
                    binding.speechStatusText.text = "发音不匹配，请重试"
                    if (currentWord != null) {
                        speakCurrentLetter(currentWord)
                    }
                    startListening()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析结果失败: ${e.message}", e)
            binding.speechStatusText.text = "请重新拼读单词"
            startListening()
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
        
        // 尝试初始化Vosk
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
        
        // 添加语音识别方法切换按钮
        setupRecognitionMethodSwitch()
        
        // 设置发音按钮的点击事件
        binding.btnPronounce.setOnClickListener {
            // 获取当前选中的单词索引
            val currentIndex = binding.viewpagerWordCards.currentItem
            val currentWord = viewModel.words.value?.getOrNull(currentIndex)
            
            if (currentWord != null) {
                speakCurrentLetter(currentWord)
                Toast.makeText(context, "正在朗读: ${currentWord.word}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "当前没有单词可朗读", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置录音按钮的触摸事件
        setupRecordButton()
        
        // 设置上一个和下一个按钮的点击事件
        setupNavigationButtons()
        
        // 观察单词列表以配置Vosk词汇表
        viewModel.words.observe(viewLifecycleOwner) { words ->
            // 配置Vosk词汇表
            if (words.isNotEmpty() && useVoskRecognition) {
                val wordTexts = words.map { it.word.lowercase().trim() }
                voskRecognitionService.configureVocabulary(wordTexts)
            }
            
            adapter.submitList(words)
            updateProgressUI(viewModel.currentWordIndex.value ?: 0, words.size)
            binding.tvEmptyState.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
        }
        
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
                Log.w(TAG, "Fragment未附加，跳过语音识别器初始化")
                return
            }
            
            // 创建语音识别器
            Log.d(TAG, "开始创建语音识别器")
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
            speechRecognizer.setRecognitionListener(createRecognitionListener())
            
            // 初始化成功后启动语音识别
            Log.d(TAG, "语音识别器初始化成功，开始监听")
            startListening()
        } catch (e: Exception) {
            Log.e(TAG, "初始化语音识别失败: ${e.message}", e)
            if (isAdded && _binding != null) {
                Toast.makeText(requireContext(), "初始化语音识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.speechStatusText.text = "语音识别初始化失败"
            }
        }
    }

    private fun startListening(isManual: Boolean = false) {
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

            // 首先尝试使用Vosk离线识别
            if (useVoskRecognition && voskRecognitionService.isReady) {
                binding.speechStatusText.text = "请拼读单词"
                voskRecognitionService.startListening(voskRecognitionListener)
                return
            }

            // 检查网络连接状态
            if (useGoogleCloudRecognition && !isNetworkAvailable()) {
                Toast.makeText(context, "网络不可用，将使用本地识别", Toast.LENGTH_SHORT).show()
                useGoogleCloudRecognition = false
            }

            // 检查设备是否支持语音识别
            val isRecognitionAvailable = requireContext().packageManager
                .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
                .isNotEmpty()

            if (!isRecognitionAvailable) {
                Toast.makeText(context, "该设备不支持语音识别", Toast.LENGTH_SHORT).show()
                binding.speechStatusText.text = "设备不支持语音识别"
                return
            }

            // 检查省电模式
            if (isPowerSaveMode()) {
                Toast.makeText(context, "设备处于省电模式，语音识别可能受限", Toast.LENGTH_SHORT).show()
            }

            // 根据设置选择识别方式
            when {
                // 使用Google云识别
                useGoogleCloudRecognition -> {
                    startGoogleCloudRecognition()
                }
                // 使用本地语音识别器
                else -> {
                    startLocalRecognition(isManual)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败: ${e.message}", e)
            binding.speechStatusText.text = "语音识别启动失败"
            Toast.makeText(context, "语音识别启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 检查网络连接状态
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo?.isConnectedOrConnecting == true
    }

    // 检查省电模式
    @SuppressLint("NewApi")
    private fun isPowerSaveMode(): Boolean {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }

    // 使用Google云识别
    private fun startGoogleCloudRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请拼读单词")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            }
            binding.speechStatusText.text = "请拼读单词"
            cloudRecognitionLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "找不到语音识别活动: ${e.message}", e)
            Toast.makeText(context, "您的设备不支持语音识别", Toast.LENGTH_SHORT).show()
            binding.speechStatusText.text = "设备不支持语音识别"
            useAlternativeRecognition = true
        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败: ${e.message}", e)
            Toast.makeText(context, "语音识别启动失败", Toast.LENGTH_SHORT).show()
            binding.speechStatusText.text = "语音识别启动失败"
            useAlternativeRecognition = true
        }
    }
    
    // 使用本地语音识别器
    private fun startLocalRecognition(isManual: Boolean) {
        if (!::speechRecognizer.isInitialized) {
            initSpeechRecognizer()
            return
        }
        
        try {
            // 配置超时
            recognitionTimeout?.cancel()
            recognitionTimeout = launch {
                delay(10000) // 10秒超时
                if (isAdded && _binding != null) {
                    binding.speechStatusText.text = "识别超时，请重试"
                    speechRecognizer.cancel()
                    startListening()
                }
            }
            
            // 开始识别
            binding.speechStatusText.text = "请拼读单词"
            speechRecognizer.startListening(speechRecognizerIntent)
            isManualRecording = isManual
        } catch (e: Exception) {
            Log.e(TAG, "本地语音识别器启动失败: ${e.message}", e)
            binding.speechStatusText.text = "语音识别启动失败"
            
            // 尝试重新初始化
            try {
                speechRecognizer.destroy()
                initSpeechRecognizer()
            } catch (innerE: Exception) {
                Log.e(TAG, "重新初始化语音识别器失败: ${innerE.message}", innerE)
            }
        }
    }

    // 创建语音识别监听器
    private fun createRecognitionListener(): AndroidRecognitionListener {
        return object : AndroidRecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isAdded || _binding == null) return
                Log.d(TAG, "语音识别准备就绪")
                binding.speechStatusText.text = "请拼读单词"
                recognitionTimeout?.cancel()
            }
            
            override fun onBeginningOfSpeech() {
                if (!isAdded || _binding == null) return
                Log.d(TAG, "开始语音输入")
                binding.speechStatusText.text = "正在聆听..."
                recognitionTimeout?.cancel()
            }
            
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                if (!isAdded || _binding == null) return
                Log.d(TAG, "语音输入结束")
                binding.speechStatusText.text = "识别中..."
            }
            
            override fun onError(error: Int) {
                if (!isAdded || _binding == null) return
                
                val errorMessage = getErrorMessage(error)
                Log.w(TAG, "语音识别错误: $errorMessage (错误码: $error)")
                
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
                        Log.e(TAG, "重启语音识别失败: ${e.message}")
                    }
                }
            }
            
            override fun onResults(results: Bundle?) {
                if (!isAdded || _binding == null) return
                
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d(TAG, "语音识别结果: $matches")
                
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0].lowercase(Locale.US)
                    handleRecognitionResult(spokenText)
                } else {
                    Log.w(TAG, "未能识别语音内容")
                    binding.speechStatusText.text = "未能识别，请重试"
                    startListening()
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // 获取错误消息
    private fun getErrorMessage(error: Int): String {
        return when (error) {
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
    }

    // 处理识别结果
    private fun handleRecognitionResult(spokenText: String) {
        binding.tvSpokenLetter.text = "您说: $spokenText"
        
        val currentWord = viewModel.words.value?.getOrNull(viewModel.currentWordIndex.value ?: -1)
        if (currentWord != null && spokenText == currentWord.word.lowercase(Locale.US)) {
            Log.d(TAG, "单词拼读正确: $spokenText")
            binding.speechStatusText.text = "发音正确!"
            viewModel.markWordAsKnown(currentWord)
            goToNextWord()
        } else {
            Log.d(TAG, "单词拼读不匹配: $spokenText vs ${currentWord?.word}")
            binding.speechStatusText.text = "发音不匹配，请重试"
            Toast.makeText(requireContext(), "发音不匹配: $spokenText", Toast.LENGTH_SHORT).show()
            if (currentWord != null) {
                speakCurrentLetter(currentWord)
            }
            startListening()
        }
    }

    private fun speakCurrentLetter(word: Word?) {
        word?.let { w ->
            // 组合单词和中文翻译，中间加空格
            val textToSpeak = if (w.chineseMeaning.isNullOrEmpty()) {
                w.word
            } else {
                "${w.word} ${w.chineseMeaning}"
            }
            
            // 调用TTS发音
            ttsHelper.speak(textToSpeak)
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
        
        // 关闭Vosk资源
        if (useVoskRecognition) {
            try {
                voskRecognitionService.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "停止Vosk识别失败: ${e.message}", e)
            }
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

    private fun setupNavigationButtons() {
        // 上一个按钮点击事件
        binding.btnPrevious.setOnClickListener {
            val currentPosition = binding.viewpagerWordCards.currentItem
            if (currentPosition > 0) {
                binding.viewpagerWordCards.currentItem = currentPosition - 1
            } else {
                Toast.makeText(requireContext(), "已经是第一个单词", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 下一个按钮点击事件
        binding.btnNext.setOnClickListener {
            goToNextWord()
        }
    }

    // 添加初始化Vosk的方法
    private fun initializeVosk() {
        lifecycleScope.launch {
            try {
                val success = voskRecognitionService.initializeRecognizer()
                useVoskRecognition = success
                
                if (success) {
                    Log.d(TAG, "Vosk初始化成功")
                    
                    // 观察单词列表以配置Vosk词汇表
                    viewModel.words.observe(viewLifecycleOwner) { words ->
                        if (words.isNotEmpty()) {
                            val wordTexts = words.map { it.word.lowercase().trim() }
                            voskRecognitionService.configureVocabulary(wordTexts)
                            Log.d(TAG, "为Vosk配置了${wordTexts.size}个单词")
                        }
                    }
                } else {
                    Log.w(TAG, "Vosk初始化失败，将使用备选方法")
                    useAlternativeRecognition = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vosk初始化异常: ${e.message}", e)
                useVoskRecognition = false
                useAlternativeRecognition = true
            }
        }
    }
}