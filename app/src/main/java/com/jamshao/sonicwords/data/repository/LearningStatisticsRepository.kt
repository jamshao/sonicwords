package com.jamshao.sonicwords.data.repository

import androidx.lifecycle.LiveData
import com.jamshao.sonicwords.data.dao.LearningStatisticsDao
import com.jamshao.sonicwords.data.model.LearningStatistics
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学习统计数据仓库
 * 负责处理学习统计数据的存储和获取
 */
@Singleton
class LearningStatisticsRepository @Inject constructor(
    private val learningStatisticsDao: LearningStatisticsDao
) {
    /**
     * 获取所有学习统计数据
     */
    fun getAllStatistics(): Flow<List<LearningStatistics>> {
        return learningStatisticsDao.getAllStatisticsFlow()
    }
    
    /**
     * 获取最近n天的学习统计数据
     */
    suspend fun getRecentStatistics(days: Int): List<LearningStatistics> {
        return learningStatisticsDao.getRecentStatistics(days)
    }
    
    /**
     * 获取指定日期范围内的学习统计数据
     */
    suspend fun getStatisticsInRange(startDate: String, endDate: String): List<LearningStatistics> {
        return learningStatisticsDao.getStatisticsInRange(startDate, endDate)
    }
    
    /**
     * 获取今日的学习统计数据
     * 如果今日没有数据，则创建一个新的
     */
    fun getTodayStatistics(): Flow<LearningStatistics?> {
        val today = LocalDate.now().toString()
        return learningStatisticsDao.getStatisticsByDate(today)
    }
    
    /**
     * 更新学习单词数
     */
    suspend fun updateLearnedCount(increment: Int = 1) {
        val todayStr = LocalDate.now().toString()
        val today = learningStatisticsDao.getStatisticsByDateSync(todayStr) ?: 
                LearningStatistics(date = todayStr)
        
        val updated = today.copy(learnedCount = today.learnedCount + increment)
        learningStatisticsDao.insert(updated)
    }
    
    /**
     * 更新复习单词数
     */
    suspend fun updateReviewCount(increment: Int = 1) {
        val todayStr = LocalDate.now().toString()
        val today = learningStatisticsDao.getStatisticsByDateSync(todayStr) ?: 
                LearningStatistics(date = todayStr)
        
        val updated = today.copy(reviewCount = today.reviewCount + increment)
        learningStatisticsDao.insert(updated)
    }
    
    /**
     * 更新正确回答数
     */
    suspend fun updateCorrectCount(increment: Int = 1) {
        val todayStr = LocalDate.now().toString()
        val today = learningStatisticsDao.getStatisticsByDateSync(todayStr) ?: 
                LearningStatistics(date = todayStr)
        
        val updated = today.copy(correctCount = today.correctCount + increment)
        learningStatisticsDao.insert(updated)
    }
    
    /**
     * 更新错误回答数
     */
    suspend fun updateErrorCount(increment: Int = 1) {
        val todayStr = LocalDate.now().toString()
        val today = learningStatisticsDao.getStatisticsByDateSync(todayStr) ?: 
                LearningStatistics(date = todayStr)
        
        val updated = today.copy(errorCount = today.errorCount + increment)
        learningStatisticsDao.insert(updated)
    }
    
    /**
     * 更新学习时间
     */
    suspend fun updateStudyTime(timeMillis: Long) {
        val todayStr = LocalDate.now().toString()
        val today = learningStatisticsDao.getStatisticsByDateSync(todayStr) ?: 
                LearningStatistics(date = todayStr)
        
        val updated = today.copy(studyTimeMillis = today.studyTimeMillis + timeMillis)
        learningStatisticsDao.insert(updated)
    }
    
    /**
     * 更新掌握单词数
     */
    suspend fun updateMasteredCount(count: Int) {
        val todayStr = LocalDate.now().toString()
        val today = learningStatisticsDao.getStatisticsByDateSync(todayStr) ?: 
                LearningStatistics(date = todayStr)
        
        val updated = today.copy(masteredCount = count)
        learningStatisticsDao.insert(updated)
    }

    suspend fun updateStatistics(statistics: LearningStatistics) {
        learningStatisticsDao.insert(statistics)
    }

    suspend fun getStatisticsByDate(date: String): LearningStatistics? {
        return learningStatisticsDao.getStatisticsByDateSync(date)
    }
    
    /**
     * 获取所有学习统计数据（suspend版本）
     */
    suspend fun getAllStatisticsSync(): List<LearningStatistics> {
        return learningStatisticsDao.getAllStatistics()
    }
}
