package com.jamshao.sonicwords.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 翻译服务接口
 * 注：实际应用中可能需要替换成自己的翻译API或使用ML Kit
 */
interface TranslationService {
    @GET("translate_a/single")
    suspend fun translate(
        @Query("client") client: String = "gtx",
        @Query("sl") sourceLanguage: String = "en",
        @Query("tl") targetLanguage: String = "zh-CN",
        @Query("dt") detailType: String = "t",
        @Query("q") text: String
    ): List<List<List<String>>>
}

data class TranslationResponse(
    val data: TranslationData
)

data class TranslationData(
    val translations: List<Translation>
)

data class Translation(
    val translatedText: String
)

object TranslationApi {
    private const val BASE_URL = "https://translation.googleapis.com/language/translate/v2/"
    private const val API_KEY = "YOUR_API_KEY" // 需要替换为实际的 API Key

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: TranslationService = retrofit.create(TranslationService::class.java)
} 