package com.vibe.app.data.dto

import com.google.gson.annotations.SerializedName

data class OpenRouterModelsResponse(
    @SerializedName("data")
    val data: List<OpenRouterModel>
)

data class OpenRouterModel(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("pricing")
    val pricing: OpenRouterPricing? = null
)

data class OpenRouterPricing(
    @SerializedName("prompt")
    val prompt: String? = "0",

    @SerializedName("completion")
    val completion: String? = "0"
) {
    val isFree: Boolean
        get() = (prompt == "0" || prompt == "0.0") && (completion == "0" || completion == "0.0")
}
