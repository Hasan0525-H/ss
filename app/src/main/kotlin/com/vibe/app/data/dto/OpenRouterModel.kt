package com.vibe.app.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterModelsResponse(

    @SerialName("data")
    val data: List<OpenRouterModel> =
        emptyList(),
)

@Serializable
data class OpenRouterModel(

    /*
     * Exact OpenRouter model ID.
     *
     * Example:
     * google/gemini-2.5-pro
     *
     * This value must be stored and sent
     * exactly as returned by OpenRouter.
     */
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String? =
        null,

    @SerialName("description")
    val description: String? =
        null,

    @SerialName("context_length")
    val contextLength: Int? =
        null,

    @SerialName("pricing")
    val pricing: OpenRouterPricing? =
        null,
)

@Serializable
data class OpenRouterPricing(

    /*
     * OpenRouter returns pricing values
     * as strings.
     *
     * IMPORTANT:
     *
     * prompt / completion are USD per token,
     * not per 1K tokens.
     *
     * Do NOT default missing prices to "0".
     * Missing pricing is unknown pricing,
     * not automatically free pricing.
     */
    @SerialName("prompt")
    val prompt: String? =
        null,

    @SerialName("completion")
    val completion: String? =
        null,

    /*
     * Optional fixed request cost.
     */
    @SerialName("request")
    val request: String? =
        null,

    /*
     * Other pricing fields returned by
     * OpenRouter.
     */
    @SerialName("image")
    val image: String? =
        null,

    @SerialName("web_search")
    val webSearch: String? =
        null,

    @SerialName("internal_reasoning")
    val internalReasoning: String? =
        null,

    @SerialName("input_cache_read")
    val inputCacheRead: String? =
        null,

    @SerialName("input_cache_write")
    val inputCacheWrite: String? =
        null,
) {

    /*
     * =========================================================
     * Raw token prices
     * =========================================================
     */

    val promptPriceDouble: Double?
        get() =
            prompt
                ?.trim()
                ?.toDoubleOrNull()

    val completionPriceDouble: Double?
        get() =
            completion
                ?.trim()
                ?.toDoubleOrNull()

    val requestPriceDouble: Double?
        get() =
            request
                ?.trim()
                ?.toDoubleOrNull()

    /*
     * =========================================================
     * Average token price
     * =========================================================
     *
     * Kept as "averagePrice" for compatibility
     * with the existing VibeApp code.
     *
     * Unit:
     * USD per token.
     */
    val averagePrice: Double?
        get() {

            val promptPrice =
                promptPriceDouble
                    ?: return null

            val completionPrice =
                completionPriceDouble
                    ?: return null

            return (
                promptPrice +
                    completionPrice
                ) / 2.0
        }

    /*
     * Correct display value for 1K tokens.
     *
     * Use this in UI when showing:
     *
     * "$... / 1K tokens"
     */
    val averagePricePer1K: Double?
        get() =
            averagePrice
                ?.times(
                    1_000.0
                )

    /*
     * Useful because OpenRouter's website
     * commonly displays prices per million
     * tokens.
     */
    val averagePricePerMillion: Double?
        get() =
            averagePrice
                ?.times(
                    1_000_000.0
                )

    val promptPricePer1K: Double?
        get() =
            promptPriceDouble
                ?.times(
                    1_000.0
                )

    val completionPricePer1K: Double?
        get() =
            completionPriceDouble
                ?.times(
                    1_000.0
                )

    val promptPricePerMillion: Double?
        get() =
            promptPriceDouble
                ?.times(
                    1_000_000.0
                )

    val completionPricePerMillion: Double?
        get() =
            completionPriceDouble
                ?.times(
                    1_000_000.0
                )

    /*
     * =========================================================
     * Free model detection
     * =========================================================
     *
     * A model is considered free for normal
     * text chat only when:
     *
     * - prompt price is explicitly 0
     * - completion price is explicitly 0
     * - fixed request price is either absent
     *   or explicitly 0
     *
     * Missing prompt/completion pricing must
     * NOT be interpreted as free.
     */
    val isFree: Boolean
        get() {

            val promptPrice =
                promptPriceDouble
                    ?: return false

            val completionPrice =
                completionPriceDouble
                    ?: return false

            val fixedRequestPrice =
                requestPriceDouble

            return (
                promptPrice == 0.0 &&
                    completionPrice == 0.0 &&
                    (
                        fixedRequestPrice == null ||
                            fixedRequestPrice == 0.0
                        )
                )
        }
}
