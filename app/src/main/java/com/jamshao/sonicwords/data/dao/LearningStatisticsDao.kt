package com.jamshao.sonicwords.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jamshao.sonicwords.data.model.LearningStatistics
import kotlinx.coroutines.flow.Flow

/**
 * 学习统计数据访问对象
 */
@Dao
interface LearningStatisticsDao {
    
    /**
     * 插入学习统计数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatistics(statistics: LearningStatistics)
    
    /**
     * 更新学习统计数据
     */
    @Update
    suspend fun updateStatistics(statistics: LearningStatistics)
    
    /**
     * 获取指定日期的学习统计数据
     */
    @Query("SELECT * FROM learning_statistics WHERE date = :date")
    fun getStatisticsByDate(date: String): Flow<LearningStatistics?>
    
    /**
     * 获取所有学习统计数据（Flow版本）
     */
    @Query("SELECT * FROM learning_statistics ORDER BY date DESC")
    fun getAllStatisticsFlow(): Flow<List<LearningStatistics>>
    
    /**
     * 获取最近n天的学习统计数据
     */
    @Query("SELECT * FROM learning_statistics ORDER BY date DESC LIMIT :days")
    fun getRecentStatistics(days: Int): List<LearningStatistics>
    
    /**
     * 获取指定日期范围内的学习统计数据
     */
    @Query("SELECT * FROM learning_statistics WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun getStatisticsInRange(startDate: String, endDate: String): List<LearningStatistics>

    /**
     * 获取指定日期的学习统计数据（同步版本）
     */
    @Query("SELECT * FROM learning_statistics WHERE date = :date")
    suspend fun getStatisticsByDateSync(date: String): LearningStatistics?

    /**
     * 插入学习统计数据（简化命名）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(statistics: LearningStatistics)

    /**
     * 获取指定日期的学习统计数据（使用Long类型的日期）
     */
    @Query("SELECT * FROM learning_statistics WHERE date = :date")
    suspend fun getStatisticsByDate(date: Long): LearningStatistics?

    /**
     * 插入或更新学习统计数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(statistics: LearningStatistics)

    /**
     * 获取所有学习统计数据（suspend版本）
     */
    @Query("SELECT * FROM learning_statistics ORDER BY date DESC")
    suspend fun getAllStatistics(): List<LearningStatistics>

    /**
     * 根据日期删除学习统计数据
     */
    @Query("DELETE FROM learning_statistics WHERE date = :date")
    suspend fun deleteStatisticsByDate(date: Long)
}
