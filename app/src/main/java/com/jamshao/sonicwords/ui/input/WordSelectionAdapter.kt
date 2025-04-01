package com.jamshao.sonicwords.ui.input

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.jamshao.sonicwords.databinding.ItemSelectableWordBinding

/**
 * 单词选择适配器，用于OCR识别后选择需要添加的单词
 */
class WordSelectionAdapter : RecyclerView.Adapter<WordSelectionAdapter.ViewHolder>() {

    // 单词列表，包含单词和是否选中的状态
    private val words = mutableListOf<WordSelection>()
    
    // 当前选中的单词更改时的回调
    private var onSelectionChangedListener: ((Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectableWordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wordSelection = words[position]
        holder.bind(wordSelection)
    }

    override fun getItemCount(): Int = words.size

    /**
     * 提交单词列表
     */
    fun submitList(wordList: List<String>) {
        words.clear()
        words.addAll(wordList.map { WordSelection(it, true) })
        notifyDataSetChanged()
        onSelectionChangedListener?.invoke(getSelectedCount())
    }
    
    /**
     * 全选所有单词
     */
    fun selectAll() {
        for (i in words.indices) {
            words[i] = words[i].copy(isSelected = true)
        }
        notifyDataSetChanged()
        onSelectionChangedListener?.invoke(getSelectedCount())
    }
    
    /**
     * 取消全选
     */
    fun deselectAll() {
        for (i in words.indices) {
            words[i] = words[i].copy(isSelected = false)
        }
        notifyDataSetChanged()
        onSelectionChangedListener?.invoke(getSelectedCount())
    }
    
    /**
     * 获取选中的单词数量
     */
    fun getSelectedCount(): Int {
        return words.count { it.isSelected }
    }
    
    /**
     * 获取选中的单词列表
     */
    fun getSelectedWords(): List<String> {
        return words.filter { it.isSelected }.map { it.word }
    }
    
    /**
     * 设置选择变化监听器
     */
    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        onSelectionChangedListener = listener
    }

    inner class ViewHolder(private val binding: ItemSelectableWordBinding) : RecyclerView.ViewHolder(binding.root) {
        private val checkBox: CheckBox = binding.cbSelectWord

        init {
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    words[position] = words[position].copy(isSelected = isChecked)
                    onSelectionChangedListener?.invoke(getSelectedCount())
                }
            }
        }

        fun bind(wordSelection: WordSelection) {
            checkBox.text = wordSelection.word
            checkBox.isChecked = wordSelection.isSelected
        }
    }
}

/**
 * 单词选择数据类
 */
data class WordSelection(
    val word: String,
    val isSelected: Boolean = true
) 