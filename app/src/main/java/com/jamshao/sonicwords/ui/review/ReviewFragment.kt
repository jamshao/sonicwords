package com.jamshao.sonicwords.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.jamshao.sonicwords.databinding.FragmentReviewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.HapticFeedbackConstants

@AndroidEntryPoint
class ReviewFragment : Fragment() {
    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReviewViewModel by viewModels()
    private lateinit var wordCardAdapter: WordCardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupObservers()
        setupButtons()
        // 默认使用间隔重复模式
        viewModel.loadReviewWords(ReviewMode.SPACED_REPETITION)
    }

    private fun setupViews() {
        // 设置 ViewPager2
        wordCardAdapter = WordCardAdapter()
        binding.viewpagerWordCards.apply {
            adapter = wordCardAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    viewModel.updateCurrentWord(position)
                }
            })
        }

        // 设置复习模式选择
        binding.rgReviewMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.rbSpacedRepetition.id -> ReviewMode.SPACED_REPETITION
                binding.rbErrorPriority.id -> ReviewMode.ERROR_PRIORITY
                binding.rbCustom.id -> ReviewMode.CUSTOM
                binding.rbMemoryCurve.id -> ReviewMode.MEMORY_CURVE
                else -> ReviewMode.SPACED_REPETITION
            }
            viewModel.loadReviewWords(mode)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.reviewWords.observe(viewLifecycleOwner) { words ->
                wordCardAdapter.submitList(words)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.reviewCount.observe(viewLifecycleOwner) { count ->
                binding.tvReviewCount.text = "今日复习: $count"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.correctRate.observe(viewLifecycleOwner) { rate ->
                binding.tvCorrectRate.text = "正确率: %.1f%%".format(rate)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.studyTime.observe(viewLifecycleOwner) { time ->
                val minutes = time / (1000 * 60)
                binding.tvStudyTime.text = "学习时长: ${minutes}分钟"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.remainingWords.observe(viewLifecycleOwner) { count ->
                binding.tvRemainingWords.text = "剩余单词: $count"
            }
        }
    }

    private fun setupButtons() {
        binding.btnUnknown.setOnClickListener { v ->
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 视觉反馈
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            
            val currentWord = viewModel.currentWord.value
            if (currentWord != null) {
                viewModel.updateWordStatus(currentWord, false)
                moveToNextWord()
            }
        }

        binding.btnKnown.setOnClickListener { v ->
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 视觉反馈
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            
            val currentWord = viewModel.currentWord.value
            if (currentWord != null) {
                viewModel.updateWordStatus(currentWord, true)
                moveToNextWord()
            }
        }

        binding.btnNext.setOnClickListener { v ->
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 视觉反馈
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            
            moveToNextWord()
        }
    }

    private fun moveToNextWord() {
        val currentPosition = binding.viewpagerWordCards.currentItem
        val totalWords = wordCardAdapter.itemCount
        if (currentPosition < totalWords - 1) {
            binding.viewpagerWordCards.currentItem = currentPosition + 1
        } else {
            Toast.makeText(requireContext(), "复习完成！", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 