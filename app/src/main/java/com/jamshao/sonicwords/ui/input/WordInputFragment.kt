package com.jamshao.sonicwords.ui.input

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jamshao.sonicwords.R
import com.jamshao.sonicwords.databinding.FragmentWordInputBinding
import com.jamshao.sonicwords.data.entity.WordWithTranslation
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class WordInputFragment : Fragment() {

    private var _binding: FragmentWordInputBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WordInputViewModel by viewModels()
    private lateinit var translationAdapter: TranslationAdapter
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    // 麦克风权限请求
    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognitionInternal()
        } else {
            Snackbar.make(binding.root, R.string.voice_permission_required, Snackbar.LENGTH_LONG).show()
        }
    }
    
    // 相机权限请求
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Snackbar.make(binding.root, "需要相机权限以使用OCR功能", Snackbar.LENGTH_LONG).show()
        }
    }
    
    // 相机拍照
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            recognizeTextFromImage(bitmap)
        }
    }
    
    // 语音识别
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.let { 
                if (it.isNotEmpty()) {
                    val recognizedText = it[0]
                    binding.wordsInputEditText.setText(recognizedText)
                    viewModel.processInput(recognizedText)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWordInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }
    
    private fun setupRecyclerView() {
        translationAdapter = TranslationAdapter(
            onEditClick = { wordTranslation ->
                showEditTranslationDialog(wordTranslation)
            },
            onRemoveClick = { wordTranslation ->
                viewModel.removeWord(wordTranslation)
            }
        )
        
        binding.translationRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = translationAdapter
        }
    }
    
    private fun setupObservers() {
        viewModel.translations.observe(viewLifecycleOwner) { translations ->
            if (translations.isEmpty()) {
                binding.noTranslationText.visibility = View.VISIBLE
                binding.translationRecyclerView.visibility = View.GONE
            } else {
                binding.noTranslationText.visibility = View.GONE
                binding.translationRecyclerView.visibility = View.VISIBLE
                translationAdapter.submitList(translations)
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
        }
        
        viewModel.ocrResult.observe(viewLifecycleOwner) { text ->
            if (!text.isNullOrBlank()) {
                binding.wordsInputEditText.setText(text)
                viewModel.processInput(text)
            }
        }
    }
    
    private fun setupClickListeners() {
        // 输入文本变化监听
        binding.wordsInputEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val text = binding.wordsInputEditText.text.toString()
                if (text.isNotBlank()) {
                    viewModel.processInput(text)
                }
                true
            } else {
                false
            }
        }
        
        // 保存按钮
        binding.saveButton.setOnClickListener {
            // 获取当前输入框中的文本
            val currentText = binding.wordsInputEditText.text.toString().trim()
            if (currentText.isNotBlank()) {
                // 解析输入的文本，显示单词选择对话框
                val words = viewModel.parseInputTextToWords(currentText)
                if (words.isNotEmpty()) {
                    showWordSelectionDialog(words)
                } else {
                    Snackbar.make(binding.root, "未能解析出有效的单词", Snackbar.LENGTH_LONG).show()
                }
            } else {
                // 当前输入为空，但可能已经通过其他方式添加了单词（如OCR、语音）
                viewModel.saveWords()
            }
        }
        
        // 相机按钮
        binding.cameraButton.setOnClickListener {
            checkCameraPermission()
        }
        
        // 语音按钮
        binding.voiceButton.setOnClickListener {
            checkMicrophonePermission()
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showCameraPermissionRationale()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun showCameraPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_required)
            .setPositiveButton(R.string.authorize) { _, _ ->
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun openCamera() {
        takePictureLauncher.launch(null)
    }
    
    private fun recognizeTextFromImage(bitmap: android.graphics.Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        binding.progressBar.visibility = View.VISIBLE
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                binding.progressBar.visibility = View.GONE
                val recognizedText = visionText.text
                if (recognizedText.isNotBlank()) {
                    val extractedWords = viewModel.extractWordsFromOcr(recognizedText)
                    if (extractedWords.isNotEmpty()) {
                        showWordSelectionDialog(extractedWords)
                    } else {
                        Snackbar.make(binding.root, "未能识别到有效的英文单词", Snackbar.LENGTH_LONG).show()
                    }
                } else {
                    Snackbar.make(binding.root, "未能识别到文字", Snackbar.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Snackbar.make(binding.root, "文字识别失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }
    
    /**
     * 显示单词选择对话框
     */
    private fun showWordSelectionDialog(words: List<String>) {
        // 创建对话框视图
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_select_words, null)
        
        // 创建单词选择适配器
        val adapter = WordSelectionAdapter()
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvWords)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        // 添加全选/取消全选按钮的点击事件
        val selectAllButton = dialogView.findViewById<View>(R.id.btnSelectAll)
        val deselectAllButton = dialogView.findViewById<View>(R.id.btnDeselectAll)
        
        selectAllButton.setOnClickListener { adapter.selectAll() }
        deselectAllButton.setOnClickListener { adapter.deselectAll() }
        
        // 提交单词列表到适配器
        adapter.submitList(words)
        
        // 创建并显示对话框
        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择要添加的单词")
            .setView(dialogView)
            .setPositiveButton("添加选中单词") { _, _ ->
                val selectedWords = adapter.getSelectedWords()
                if (selectedWords.isNotEmpty()) {
                    // 显示选中的单词，并翻译
                    binding.wordsInputEditText.setText(selectedWords.joinToString(" "))
                    viewModel.processInput(selectedWords.joinToString(" "))
                    
                    // 使用延迟确保单词处理完成后再保存
                    binding.root.postDelayed({
                        viewModel.saveWords()
                    }, 800) // 延迟800毫秒，确保翻译API有足够时间处理
                    
                    // 显示加载提示
                    Snackbar.make(binding.root, "正在处理并保存选中的单词...", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        // 在选择数量变化时更新按钮文本
        adapter.setOnSelectionChangedListener { selectedCount ->
            alertDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.text = 
                "添加选中单词 (${selectedCount})"
        }
        
        alertDialog.show()
    }
    
    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startSpeechRecognitionInternal()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                showMicrophonePermissionRationale()
            }
            else -> {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun showMicrophonePermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("需要麦克风权限")
            .setMessage("为了使用语音识别功能，需要获取麦克风权限。")
            .setPositiveButton("授权") { _, _ ->
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun startSpeechRecognition() {
        checkMicrophonePermission()
    }
    
    private fun startSpeechRecognitionInternal() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            // 不再检查设备支持，直接尝试启动语音识别
            // 即使SpeechRecognizer.isRecognitionAvailable返回false，设备也可能支持语音识别
            try {
                // 直接尝试启动语音识别活动
                speechRecognizerLauncher.launch(intent)
                Log.d("WordInputFragment", "语音识别活动已启动")
            } catch (e: Exception) {
                Log.e("WordInputFragment", "启动语音识别失败: ${e.message}", e)
                
                // 尝试直接启动系统语音识别活动作为备选方案
                try {
                    startActivity(intent)
                    Log.d("WordInputFragment", "已尝试使用系统默认语音识别")
                } catch (e2: Exception) {
                    // 如果两种方法都失败，才显示不支持的提示
                    Snackbar.make(
                        binding.root,
                        "无法启动语音识别，请检查您的设备设置或权限",
                        Snackbar.LENGTH_LONG
                    ).show()
                    Log.e("WordInputFragment", "备选语音识别方法也失败", e2)
                }
            }
        } catch (e: Exception) {
            Snackbar.make(
                binding.root,
                "语音识别初始化失败: ${e.message ?: "未知错误"}",
                Snackbar.LENGTH_LONG
            ).show()
            Log.e("WordInputFragment", "语音识别初始化失败", e)
        }
    }
    
    private fun showEditTranslationDialog(wordTranslation: WordWithTranslation) {
        val editTextView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_translation, null) as ViewGroup
        val editText = editTextView.findViewById<TextInputEditText>(R.id.etChineseMeaning)
        editText.setText(wordTranslation.translation)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑翻译")
            .setView(editTextView)
            .setPositiveButton("确定") { _, _ ->
                val newTranslation = editText.text.toString()
                if (newTranslation.isNotBlank()) {
                    viewModel.editTranslation(wordTranslation, newTranslation)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 