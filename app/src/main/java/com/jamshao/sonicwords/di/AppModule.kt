package com.jamshao.sonicwords.di

import android.content.Context
import androidx.room.Room
import com.jamshao.sonicwords.data.dao.LearningStatisticsDao
import com.jamshao.sonicwords.data.dao.WordDao
import com.jamshao.sonicwords.data.database.AppDatabase
import com.jamshao.sonicwords.network.TranslationApi
import com.jamshao.sonicwords.service.TranslationService
import com.jamshao.sonicwords.service.impl.MLKitTranslationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideWordDao(database: AppDatabase): WordDao {
        return database.wordDao()
    }

    @Provides
    @Singleton
    fun provideLearningStatisticsDao(database: AppDatabase): LearningStatisticsDao {
        return database.learningStatisticsDao()
    }

    @Provides
    @Singleton
    fun provideTranslationApi(): TranslationApi {
        return Retrofit.Builder()
            .baseUrl(TranslationApi.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TranslationApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    
    @Singleton
    @Provides
    fun provideTranslationService(translationService: MLKitTranslationService): TranslationService {
        return translationService
    }
} 