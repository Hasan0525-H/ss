package com.vibe.app.data.network

import com.vibe.app.data.dto.OpenRouterModel
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
     * جلب قائمة الموديلات من OpenRouter مع إمكانية الفلترة
     * بين المجاني والمدفوع (مع ترتيب المدفوع تصاعدياً حسب السعر)
     */
    suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean
    ): List<OpenRouterModel> {
        return try {
            val formattedToken = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
            
            val response: OpenRouterModelsResponse = client.get("https://openrouter.ai/api/v1/models") {
                header("Authorization", formattedToken)
                header("HTTP-Referer", "https://vibe.app")
                header("X-Title", "Vibe App")
            }.body()

            if (isFreeOnly) {
                // تصفية النماذج المجانية فقط
                response.data.filter { it.pricing?.isFree == true }
            } else {
                // تصفية النماذج المدفوعة وترتيبها تصاعدياً من الأقل سعراً للأعلى
                response.data
                    .filter { it.pricing?.isFree == false }
                    .sortedBy { it.pricing?.averagePrice ?: Double.MAX_VALUE }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * جلب الاستجابة الخام للموديلات مباشرة عند الحاجة
     */
    suspend fun getModels(
        token: String,
        sort: String? = null
    ): OpenRouterModelsResponse {
        val formattedToken = if (token.startsWith("Bearer ")) token else "Bearer $token"
        return client.get("https://openrouter.ai/api/v1/models") {
            header("Authorization", formattedToken)
            sort?.let { parameter("sort", it) }
        }.body()
    }
}
