package com.vibe.app.data.network

import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.dto.OpenRouterModelsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import javax.inject.Inject

class OpenRouterModelsAPI @Inject constructor(
    private val client: HttpClient
) {

    /**
     * جلب أحدث قائمة موديلات OpenRouter
     *
     * isFreeOnly:
     * true  = مجاني فقط
     * false = مدفوع فقط مرتبة حسب السعر الأقل
     */
    suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean
    ): List<OpenRouterModel> {

        val formattedToken = if (apiKey.startsWith("Bearer ")) {
            apiKey
        } else {
            "Bearer $apiKey"
        }

        val response: OpenRouterModelsResponse = client.get(
            "https://openrouter.ai/api/v1/models"
        ) {
            header("Authorization", formattedToken)
            header("HTTP-Referer", "https://vibe.app")
            header("X-Title", "Vibe App")
        }.body()

        return if (isFreeOnly) {
            response.data
                .filter { it.pricing?.isFree == true }
                .sortedBy { it.name ?: it.id }
        } else {
            response.data
                .filter { it.pricing?.isFree == false }
                .sortedBy {
                    it.pricing?.averagePrice ?: Double.MAX_VALUE
                }
        }
    }

    /**
     * جلب جميع الموديلات بدون فلترة
     */
    suspend fun getModels(
        token: String
    ): OpenRouterModelsResponse {

        val formattedToken = if (token.startsWith("Bearer ")) {
            token
        } else {
            "Bearer $token"
        }

        return client.get(
            "https://openrouter.ai/api/v1/models"
        ) {
            header("Authorization", formattedToken)
            header("HTTP-Referer", "https://vibe.app")
            header("X-Title", "Vibe App")
        }.body()
    }
}
