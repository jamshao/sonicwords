package com.jamshao.sonicwords.ui.wordlist

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jamshao.sonicwords.R
import com.jamshao.sonicwords.ui.wordlist.WordAdapter
import com.jamshao.sonicwords.data.entity.Word
import com.jamshao.sonicwords.databinding.FragmentWordListBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.view.HapticFeedbackConstants

@AndroidEntryPoint
class WordListFragment : Fragment() {

    private var _binding: FragmentWordListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WordListViewModel by viewModels()
    private lateinit var adapter: WordAdapter
    
    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menu.clear()
            if (::adapter.isInitialized && adapter.isInSelectionMode()) {
                menuInflater.inflate(R.menu.menu_word_list_selection, menu)
            }
        }
        
        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.action_select_all -> {
                    if (::adapter.isInitialized) {
                        adapter.selectAll()
                    }
                    true
                }
                R.id.action_delete_selected -> {
                    if (::adapter.isInitialized) {
                        deleteSelectedItems()
                    }
                    true
                }
                android.R.id.home -> {
                    if (::adapter.isInitialized && adapter.isInSelectionMode()) {
                        exitSelectionMode()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importWordsFromFile(uri)
            }
        }
    }

    // 添加排序类型枚举
    enum class SortType {
        WORD,          // 按单词字母顺序
        ERROR_COUNT,   // 按错误次数
        CORRECT_COUNT, // 按正确次数
        LAST_STUDY     // 按上次学习时间
    }
    
    // 当前排序类型
    private var currentSortType = SortType.WORD

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWordListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        setupItemTouchHelper()
        setupSortButtons()
        
        // 添加菜单提供者
        val menuHost = requireActivity()
        menuHost.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        adapter = WordAdapter(
            onWordClick = { _: Word ->
                // TODO: 处理单词点击事件
            },
            onSelectionChanged = { count ->
                updateSelectionUI(count)
                updateActionBarTitle()
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WordListFragment.adapter
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allWords.collectLatest { words ->
                    // 先应用排序
                    val sortedWords = when (currentSortType) {
                        SortType.WORD -> words.sortedBy { it.word }
                        SortType.ERROR_COUNT -> words.sortedByDescending { it.errorCount }
                        SortType.CORRECT_COUNT -> words.sortedByDescending { it.familiarity }
                        SortType.LAST_STUDY -> words.sortedByDescending { it.lastStudyTime ?: 0 }
                    }
                    
                    adapter.submitList(sortedWords)
                    binding.tvEmptyState.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabAddWord.setOnClickListener { v ->
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 视觉反馈
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            
            // TODO: 处理添加单词事件
        }

        binding.btnDeleteSelected.setOnClickListener { v ->
            if (!::adapter.isInitialized) return@setOnClickListener
            
            // 触觉反馈
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            // 视觉反馈
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }.start()
            
            val selectedWords = adapter.getSelectedItems()
            if (selectedWords.isNotEmpty()) {
                // 显示确认对话框
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除单词")
                    .setMessage("确定要删除选中的 ${selectedWords.size} 个单词吗？")
                    .setPositiveButton("删除") { _, _ ->
                        selectedWords.forEach { word: Word -> viewModel.deleteWord(word) }
                        Snackbar.make(
                            binding.root,
                            "已删除 ${selectedWords.size} 个单词",
                            Snackbar.LENGTH_LONG
                        ).setAction("撤销") {
                            // 提供撤销功能
                            selectedWords.forEach { word: Word -> viewModel.insertWord(word) }
                        }.show()
                        exitSelectionMode()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                Snackbar.make(binding.root, "请先选择要删除的单词", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val wordToDelete = adapter.currentList[position]
                viewModel.deleteWord(wordToDelete)

                Snackbar.make(binding.root, "单词已删除", Snackbar.LENGTH_LONG)
                    .setAction("撤销") { 
                        viewModel.insertWord(wordToDelete)
                    }
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun updateSelectionUI(selectedCount: Int) {
        binding.btnDeleteSelected.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
    }

    private fun showImportOptionsDialog() {
        val options = arrayOf("导入TXT文件", "导入CSV文件")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择导入方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFileChooser("text/plain")
                    1 -> openFileChooser("text/csv")
                }
            }
            .show()
    }

    private fun openFileChooser(mimeType: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
        }
        openFileLauncher.launch(intent)
    }

    private fun importWordsFromFile(uri: Uri) {
        try {
            val words = mutableListOf<Word>()

            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { processLine(it, words) }
                    }
                }
            }

            if (words.isNotEmpty()) {
                viewModel.insertWords(words)
                Snackbar.make(
                    binding.root,
                    "成功导入 ${words.size} 个单词",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                Snackbar.make(
                    binding.root,
                    "未能从文件中导入单词",
                    Snackbar.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {
            Snackbar.make(
                binding.root,
                "导入失败: ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun processLine(line: String, words: MutableList<Word>) {
        if (line.isBlank()) return

        // 支持多种分隔符: 逗号、制表符、空格
        val parts = line.split("[,\\t]+".toRegex()).map { it.trim() }

        if (parts.size >= 2) {
            val word = parts[0]
            val meaning = parts[1]

            if (word.isNotBlank() && meaning.isNotBlank()) {
                words.add(Word(word = word, meaning = meaning))
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = ""
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
        return result
    }

    private fun updateActionBarTitle() {
        val actionBar = (requireActivity() as AppCompatActivity).supportActionBar
        if (::adapter.isInitialized && adapter.isInSelectionMode()) {
            val count = adapter.getSelectedItems().size
            actionBar?.title = "已选择 $count 项"
        } else {
            actionBar?.title = "单词列表"
        }
        requireActivity().invalidateOptionsMenu()
    }

    private fun exitSelectionMode() {
        if (::adapter.isInitialized) {
            adapter.toggleSelectionMode()
            updateActionBarTitle()
        }
    }

    private fun deleteSelectedItems() {
        if (!::adapter.isInitialized) return
        
        val selectedWords = adapter.getSelectedItems()
        if (selectedWords.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除单词")
                .setMessage("确定要删除选中的 ${selectedWords.size} 个单词吗？")
                .setPositiveButton("删除") { _, _ ->
                    selectedWords.forEach { word: Word -> viewModel.deleteWord(word) }
                    Snackbar.make(
                        binding.root,
                        "已删除 ${selectedWords.size} 个单词",
                        Snackbar.LENGTH_LONG
                    ).show()
                    exitSelectionMode()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 设置排序按钮
     */
    private fun setupSortButtons() {
        binding.btnSortWord.setOnClickListener {
            currentSortType = SortType.WORD
            sortCurrentList()
            updateSortButtonsUi()
        }
        
        binding.btnSortError.setOnClickListener {
            currentSortType = SortType.ERROR_COUNT
            sortCurrentList()
            updateSortButtonsUi()
        }
        
        binding.btnSortCorrect.setOnClickListener {
            currentSortType = SortType.CORRECT_COUNT
            sortCurrentList()
            updateSortButtonsUi()
        }
        
        binding.btnSortTime.setOnClickListener {
            currentSortType = SortType.LAST_STUDY
            sortCurrentList()
            updateSortButtonsUi()
        }
        
        // 默认更新UI状态
        updateSortButtonsUi()
    }
    
    /**
     * 更新排序按钮UI状态
     */
    private fun updateSortButtonsUi() {
        // 重置所有按钮样式
        binding.btnSortWord.alpha = 0.6f
        binding.btnSortError.alpha = 0.6f
        binding.btnSortCorrect.alpha = 0.6f
        binding.btnSortTime.alpha = 0.6f
        
        // 高亮当前选中的按钮
        when (currentSortType) {
            SortType.WORD -> binding.btnSortWord.alpha = 1.0f
            SortType.ERROR_COUNT -> binding.btnSortError.alpha = 1.0f
            SortType.CORRECT_COUNT -> binding.btnSortCorrect.alpha = 1.0f
            SortType.LAST_STUDY -> binding.btnSortTime.alpha = 1.0f
        }
    }
    
    /**
     * 对当前列表进行排序
     */
    private fun sortCurrentList() {
        val currentList = adapter.currentList
        
        val sortedList = when (currentSortType) {
            SortType.WORD -> currentList.sortedBy { it.word }
            SortType.ERROR_COUNT -> currentList.sortedByDescending { it.errorCount }
            SortType.CORRECT_COUNT -> currentList.sortedByDescending { it.familiarity } 
            SortType.LAST_STUDY -> currentList.sortedByDescending { it.lastStudyTime ?: 0 }
        }
        
        adapter.submitList(sortedList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}