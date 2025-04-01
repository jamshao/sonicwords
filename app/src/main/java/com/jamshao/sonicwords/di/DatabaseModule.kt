package com.jamshao.sonicwords.di

import android.content.Context
import com.jamshao.sonicwords.data.dao.LearningStatisticsDao
import com.jamshao.sonicwords.data.dao.WordDao
import com.jamshao.sonicwords.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// 删除这个模块，因为在 AppModule 中已经有相同的绑定
// 如果以后需要添加额外的 DAO 或数据库相关的绑定，可以再重新启用此模块
/*
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    @Provides
    fun provideWordDao(appDatabase: AppDatabase): WordDao {
        return appDatabase.wordDao()
    }
    
    @Provides
    fun provideLearningStatisticsDao(appDatabase: AppDatabase): LearningStatisticsDao {
        return appDatabase.learningStatisticsDao()
    }
}
*/