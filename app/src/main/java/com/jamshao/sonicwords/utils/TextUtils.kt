package com.jamshao.sonicwords.utils

/**
 * 文本处理工具类
 */
object TextUtils {
    
    /**
     * 判断是否为有效的英文单词
     */
    fun isValidEnglishWord(word: String): Boolean {
        // 至少1个字符
        if (word.isEmpty()) return false
        
        // 检查是否只包含英文字母
        return word.all { it.isLetter() }
    }
    
    /**
     * 从文本中提取英文单词
     */
    fun extractEnglishWords(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        
        // 英文单词的正则表达式，只包含英文字母
        val englishWordPattern = Regex("\\b[a-zA-Z]+\\b")
        
        // 提取所有匹配的单词，并转换为小写，去重
        return englishWordPattern.findAll(text)
            .map { it.value.lowercase().trim() }
            .filter { isValidEnglishWord(it) }
            .toList()
            .distinct()
    }
    
    /**
     * 解析输入文本为单词列表
     */
    fun parseInputText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        
        // 先尝试按逗号分隔，这是最常见的分隔方式
        if (text.contains(",")) {
            return text.split(",")
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { it.any { char -> char.isLetter() } } // 至少包含一个字母
                .toList()
        }
        
        // 其他情况使用多种分隔符：空格、分号、换行、制表符等
        val delimiters = "[ ;\n\r\t]".toRegex()
        
        return text.split(delimiters)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it.any { char -> char.isLetter() } } // 至少包含一个字母
            .toList()
    }
} 