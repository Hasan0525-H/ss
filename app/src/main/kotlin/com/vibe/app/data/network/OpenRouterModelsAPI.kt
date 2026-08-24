package com.vibe.app.data.network

import com.vibe.app.data.dto.OpenRouterModelsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import javax.inject.Inject

class OpenRouterModelsAPI @Inject constructor(
    private val client: HttpClient
) {
    /**
     * جلب قائمة جميع الموديلات المتاحة من OpenRouter
     * مع إمكانية الترتيب حسب السعر (من الأدنى إلى الأعلى)
     */
    suspend fun getModels(
        token: String,
        sort: String? = null
    ): OpenRouterModelsResponse {
        return client.get("https://openrouter.ai/api/v1/models") {
            header("Authorization", token)
            sort?.let { parameter("sort", it) }
        }.body()
    }
}
