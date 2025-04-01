package com.jamshao.sonicwords.ui.wordreview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.databinding.ItemWordReviewBinding

class WordReviewAdapter(
    private val onRatingChanged: (Word, Float) -> Unit
) : ListAdapter<Word, WordReviewAdapter.WordReviewViewHolder>(WordDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordReviewViewHolder {
        val binding = ItemWordReviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WordReviewViewHolder(binding, onRatingChanged)
    }

    override fun onBindViewHolder(holder: WordReviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WordReviewViewHolder(
        private val binding: ItemWordReviewBinding,
        private val ratingChanged: (Word, Float) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(word: Word) {
            binding.apply {
                tvWord.text = word.word
                tvMeaning.text = word.meaning
                tvChineseMeaning.text = word.chineseMeaning
                ratingBar.rating = word.familiarity

                ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
                    if (fromUser) {
                        ratingChanged(word, rating)
                    }
                }
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