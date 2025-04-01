package com.jamshao.sonicwords.data.database

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Room数据库类型转换器
 */
class Converters {
    
    /**
     * 将Instant类型转换为Long类型存储
     */
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }
    
    /**
     * 将Long类型转换回Instant类型
     */
    @TypeConverter
    fun toInstant(epochMilli: Long?): Instant? {
        return epochMilli?.let { Instant.ofEpochMilli(it) }
    }
} 