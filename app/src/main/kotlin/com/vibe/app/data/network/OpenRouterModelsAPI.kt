package com.vibe.app.data.network

import com.vibe.app.data.dto.OpenRouterModelsResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface OpenRouterModelsAPI {

    /**
     * جلب قائمة جميع الموديلات المتاحة من OpenRouter
     * مع إمكانية الترتيب حسب السعر (من الأدنى إلى الأعلى)
     */
    @GET("api/v1/models")
    suspend fun getModels(
        @Header("Authorization") token: String,
        @Query("sort") sort: String? = null
    ): OpenRouterModelsResponse
}
