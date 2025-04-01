package com.jamshao.sonicwords.ui.wordstudy

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jamshao.sonicwords.R
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.databinding.ItemWordCardBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class WordCardAdapter(
    private val onEasyClick: (Word) -> Unit,
    private val onMediumClick: (Word) -> Unit,
    private val onHardClick: (Word) -> Unit
) : ListAdapter<Word, WordCardAdapter.WordCardViewHolder>(WordDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordCardViewHolder {
        val binding = ItemWordCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WordCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WordCardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WordCardViewHolder(
        private val binding: ItemWordCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(word: Word) {
            binding.apply {
                tvWord.text = word.word
                if (word.meaning != word.chineseMeaning) {
                    tvMeaning.text = word.meaning
                    tvMeaning.visibility = View.VISIBLE
                } else {
                    tvMeaning.visibility = View.GONE
                }
                tvChineseMeaning.text = word.chineseMeaning

                btnEasy.setOnClickListener { onEasyClick(word) }
                btnMedium.setOnClickListener { onMediumClick(word) }
                btnHard.setOnClickListener { onHardClick(word) }
            }
        }
    }

    private class WordDiffCallback : DiffUtil.ItemCallback<Word>() {
        override fun areItemsTheSame(oldItem: Word, newItem: Word): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Word, newItem: Word): Boolean {
            return oldItem == newItem
        }
    }
}