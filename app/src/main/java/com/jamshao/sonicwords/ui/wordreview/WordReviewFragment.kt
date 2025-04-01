package com.jamshao.sonicwords.ui.wordreview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.viewpager2.widget.ViewPager2
import com.jamshao.sonicwords.ui.wordreview.WordReviewAdapter
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.databinding.FragmentWordReviewBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WordReviewFragment : Fragment() {

    private var _binding: FragmentWordReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WordReviewViewModel by viewModels()
    private lateinit var adapter: WordReviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWordReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupObservers()

        viewModel.loadReviewWords()
    }

    private fun setupViewPager() {
        adapter = WordReviewAdapter(
            onRatingChanged = { word, rating ->
                if (rating >= 3.0f) {
                    viewModel.markWordAsKnown(word)
                } else {
                    viewModel.markWordAsUnknown(word)
                }
                goToNextWord()
            }
        )
        binding.recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.reviewWords.observe(viewLifecycleOwner, Observer { words ->
            adapter.submitList(words)
        })

        viewModel.currentPosition.observe(viewLifecycleOwner, Observer { position ->
            binding.recyclerView.scrollToPosition(position)
        })
        
        viewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        })
        
        viewModel.error.observe(viewLifecycleOwner, Observer { error ->
            error?.let { 
                // 显示错误信息
            }
        })
    }

    private fun goToNextWord() {
        val currentPosition = (binding.recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        if (currentPosition < adapter.itemCount - 1) {
            binding.recyclerView.smoothScrollToPosition(currentPosition + 1)
            viewModel.updateCurrentWord(currentPosition + 1)
        } else {
            // TODO: 处理复习结束的逻辑，例如显示完成消息或返回
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}