package com.jamshao.sonicwords.ui.review

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.databinding.ItemWordCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WordCardAdapter : ListAdapter<Word, WordCardAdapter.WordCardViewHolder>(WordDiffCallback()) {

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

    class WordCardViewHolder(
        private val binding: ItemWordCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(word: Word) {
            binding.apply {
                tvWord.text = word.word
                tvMeaning.text = word.meaning
                tvChineseMeaning.text = word.chineseMeaning ?: word.meaning
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