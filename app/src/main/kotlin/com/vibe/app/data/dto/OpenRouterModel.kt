package com.vibe.app.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterModelsResponse(
    @SerialName("data")
    val data: List<OpenRouterModel> = emptyList()
)

@Serializable
data class OpenRouterModel(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("pricing")
    val pricing: OpenRouterPricing? = null
)

@Serializable
data class OpenRouterPricing(
    @SerialName("prompt")
    val prompt: String? = "0",

    @SerialName("completion")
    val completion: String? = "0"
) {
    val promptPriceDouble: Double
        get() = prompt?.toDoubleOrNull() ?: 0.0

    val completionPriceDouble: Double
        get() = completion?.toDoubleOrNull() ?: 0.0

    val averagePrice: Double
        get() = (promptPriceDouble + completionPriceDouble) / 2.0

    val isFree: Boolean
        get() = (prompt == "0" || prompt == "0.0" || promptPriceDouble == 0.0) &&
                (completion == "0" || completion == "0.0" || completionPriceDouble == 0.0)
}
