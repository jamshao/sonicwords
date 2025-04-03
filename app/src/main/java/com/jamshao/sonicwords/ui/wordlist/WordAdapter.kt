package com.jamshao.sonicwords.ui.wordlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.databinding.ItemWordBinding
import com.jamshao.sonicwords.R

class WordAdapter(
    private val onWordClick: (Word) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<Word, WordAdapter.WordViewHolder>(WordDiffCallback()) {

    private var selectionMode = false
    private val selectedItems = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val binding = ItemWordBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return WordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val word = getItem(position)
        holder.bind(word, selectedItems.contains(word.id), selectionMode)
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(word)
            } else {
                onWordClick(word)
            }
        }
        
        // 设置长按事件
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                toggleSelectionMode()
                toggleSelection(word)
            }
            true
        }
    }
    
    // 切换选择模式
    fun toggleSelectionMode() {
        selectionMode = !selectionMode
        selectedItems.clear()
        onSelectionChanged(0)
        notifyDataSetChanged()
    }
    
    // 检查是否处于选择模式
    fun isInSelectionMode(): Boolean = selectionMode
    
    // 退出选择模式
    fun exitSelectionMode() {
        if (selectionMode) {
            selectionMode = false
            selectedItems.clear()
            onSelectionChanged(0)
            notifyDataSetChanged()
        }
    }
    
    // 全选
    fun selectAll() {
        if (selectionMode) {
            selectedItems.clear()
            currentList.forEach { word ->
                selectedItems.add(word.id)
            }
            onSelectionChanged(selectedItems.size)
            notifyDataSetChanged()
        }
    }
    
    // 切换单个项目的选择状态
    private fun toggleSelection(word: Word) {
        if (selectedItems.contains(word.id)) {
            selectedItems.remove(word.id)
        } else {
            selectedItems.add(word.id)
        }
        onSelectionChanged(selectedItems.size)
        notifyDataSetChanged()
    }
    
    // 获取已选择的项目
    fun getSelectedItems(): List<Word> {
        return currentList.filter { word -> selectedItems.contains(word.id) }
    }
    
    class WordViewHolder(private val binding: ItemWordBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(word: Word, isSelected: Boolean, selectionMode: Boolean) {
            binding.apply {
                tvWord.text = word.word
                tvMeaning.text = word.meaning
                tvChineseMeaning.text = word.chineseMeaning ?: ""
                
                // 设置选中状态
                if (selectionMode) {
                    cbSelected.visibility = View.VISIBLE
                    cbSelected.isChecked = isSelected
                } else {
                    cbSelected.visibility = View.GONE
                }
                
                // 设置学习状态
                if (word.isLearned) {
                    tvLearningStatus.text = "已学习"
                    tvLearningStatus.background = itemView.context.getDrawable(android.R.color.holo_green_dark)
                } else {
                    tvLearningStatus.text = "新单词"
                    tvLearningStatus.background = itemView.context.getDrawable(R.color.unknown)
                }
                
                // 设置错误次数
                tvErrorCount.text = "错误: ${word.errorCount}次"
                
                // 设置正确次数 (使用familiarity*10作为正确次数的近似值)
                val correctCount = (word.familiarity * 10).toInt()
                tvCorrectCount.text = "正确: ${correctCount}次"
                
                // 设置上次练习时间
                word.lastStudyTime?.let { lastTime ->
                    val timeString = formatTimeAgo(lastTime)
                    tvLastStudyTime.text = "上次练习: $timeString"
                    tvLastStudyTime.visibility = View.VISIBLE
                } ?: run {
                    tvLastStudyTime.visibility = View.GONE
                }
                
                // 设置熟悉度
                progressFamiliarity.progress = (word.familiarity * 100).toInt()
                tvFamiliarityLabel.text = "熟悉度: ${(word.familiarity * 100).toInt()}%"
            }
        }
        
        /**
         * 格式化时间为"多久以前"的形式
         */
        private fun formatTimeAgo(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60 * 1000 -> "刚刚"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
                diff < 30 * 24 * 60 * 60 * 1000L -> "${diff / (24 * 60 * 60 * 1000)}天前"
                diff < 12 * 30 * 24 * 60 * 60 * 1000L -> "${diff / (30 * 24 * 60 * 60 * 1000L)}月前"
                else -> "${diff / (12 * 30 * 24 * 60 * 60 * 1000L)}年前"
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