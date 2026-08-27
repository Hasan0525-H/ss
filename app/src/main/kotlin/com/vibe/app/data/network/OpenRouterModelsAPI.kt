package com.vibe.app.data.network

import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.dto.OpenRouterModelsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterModelsAPI @Inject constructor(
    private val client: HttpClient,
) {

    /**
     * Fetch OpenRouter models.
     *
     * This API is used ONLY for OpenRouter.
     *
     * isFreeOnly:
     *
     * true:
     * return free models only.
     *
     * false:
     * return paid models only,
     * sorted from cheapest to most expensive.
     */
    suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean,
    ): List<OpenRouterModel> {

        val response =
            requestModels(
                apiKey = apiKey
            )

        return if (isFreeOnly) {

            response.data
                .asSequence()
                .filter { model ->

                    model.pricing
                        ?.isFree ==
                        true
                }
                .sortedBy { model ->

                    model.name
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: model.id
                }
                .toList()

        } else {

            response.data
                .asSequence()
                .filter { model ->

                    model.pricing
                        ?.isFree ==
                        false
                }
                .sortedBy { model ->

                    model.pricing
                        ?.averagePrice
                        ?: Double.MAX_VALUE
                }
                .toList()
        }
    }

    /**
     * Fetch all OpenRouter models without
     * Free / Paid filtering.
     *
     * Kept for compatibility with existing
     * code that needs the raw response object.
     */
    suspend fun getModels(
        token: String,
    ): OpenRouterModelsResponse {

        return requestModels(
            apiKey = token
        )
    }

    /**
     * Execute the OpenRouter models request.
     *
     * This is the single network path used by
     * both:
     *
     * - fetchOpenRouterModels()
     * - getModels()
     */
    private suspend fun requestModels(
        apiKey: String,
    ): OpenRouterModelsResponse {

        val normalizedApiKey =
            normalizeApiKey(
                apiKey
            )

        return client
            .get(
                OPENROUTER_MODELS_URL
            ) {

                header(
                    "Authorization",
                    "Bearer $normalizedApiKey"
                )

                /*
                 * Optional OpenRouter attribution
                 * headers.
                 */
                header(
                    "HTTP-Referer",
                    APP_REFERER
                )

                header(
                    "X-Title",
                    APP_TITLE
                )
            }
            .body()
    }

    /**
     * Normalize an API key.
     *
     * Accepted input:
     *
     * sk-or-...
     *
     * Bearer sk-or-...
     *
     * bearer sk-or-...
     *
     * The returned value NEVER includes
     * the "Bearer " prefix.
     */
    private fun normalizeApiKey(
        rawApiKey: String,
    ): String {

        val trimmed =
            rawApiKey.trim()

        val normalized =
            if (
                trimmed.startsWith(
                    prefix = "Bearer ",
                    ignoreCase = true,
                )
            ) {

                trimmed
                    .substring(
                        startIndex =
                            "Bearer ".length
                    )
                    .trim()

            } else {

                trimmed
            }

        require(
            normalized.isNotBlank()
        ) {
            "OpenRouter API key is empty"
        }

        return normalized
    }

    companion object {

        /**
         * Official OpenRouter models endpoint.
         */
        private const val OPENROUTER_MODELS_URL =
            "https://openrouter.ai/api/v1/models"

        /**
         * OpenRouter attribution headers.
         */
        private const val APP_REFERER =
            "https://vibe.app"

        private const val APP_TITLE =
            "Vibe App"
    }
}
