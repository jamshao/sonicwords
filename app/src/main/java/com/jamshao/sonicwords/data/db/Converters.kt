package com.jamshao.sonicwords.data.db

import androidx.room.TypeConverter
// import java.time.Instant // 不再需要

class Converters {
    /* // 注释掉不再需要的转换器
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun toTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }
    */
} 