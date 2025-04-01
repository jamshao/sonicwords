package com.jamshao.sonicwords.ui.input

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jamshao.sonicwords.R
import com.jamshao.sonicwords.data.entity.WordWithTranslation
import com.jamshao.sonicwords.databinding.ItemTranslationBinding

class TranslationAdapter(
    private val onEditClick: (WordWithTranslation) -> Unit,
    private val onRemoveClick: (WordWithTranslation) -> Unit
) : ListAdapter<WordWithTranslation, TranslationAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTranslationBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ItemTranslationBinding) : RecyclerView.ViewHolder(binding.root) {
        private val wordText: TextView = binding.wordText
        private val translationText: TextView = binding.translationText
        private val editButton: ImageButton = binding.editButton
        private val removeButton: ImageButton = binding.removeButton

        init {
            editButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEditClick(getItem(position))
                }
            }
            
            removeButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRemoveClick(getItem(position))
                }
            }
        }

        fun bind(item: WordWithTranslation) {
            wordText.text = item.word
            translationText.text = item.translation
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<WordWithTranslation>() {
            override fun areItemsTheSame(oldItem: WordWithTranslation, newItem: WordWithTranslation): Boolean {
                return oldItem.word == newItem.word
            }

            override fun areContentsTheSame(oldItem: WordWithTranslation, newItem: WordWithTranslation): Boolean {
                return oldItem == newItem
            }
        }
    }
} 