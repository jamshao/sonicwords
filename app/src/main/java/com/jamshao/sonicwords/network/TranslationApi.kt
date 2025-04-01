package com.jamshao.sonicwords.network

import retrofit2.http.GET
import retrofit2.http.Query

interface TranslationApi {
    @GET("translate")
    suspend fun translate(
        @Query("q") text: String,
        @Query("source") source: String = "en",
        @Query("target") target: String = "zh",
        @Query("key") apiKey: String = API_KEY
    ): TranslationResponse

    companion object {
        const val BASE_URL = "https://translation.googleapis.com/language/translate/v2/"
        const val API_KEY = "YOUR_API_KEY" // 需要替换为实际的 API Key
    }
}

data class TranslationResponse(
    val data: TranslationData
) {
    val translation: List<String>
        get() = data.translations.map { it.translatedText }
}

data class TranslationData(
    val translations: List<Translation>
)

data class Translation(
    val translatedText: String
) 