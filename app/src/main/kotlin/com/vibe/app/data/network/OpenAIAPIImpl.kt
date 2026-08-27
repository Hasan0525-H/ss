package com.vibe.app.data.network

import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.dto.OpenRouterModelsResponse
import com.vibe.app.data.dto.openai.request.ChatCompletionRequest
import com.vibe.app.data.dto.openai.request.ResponsesRequest
import com.vibe.app.data.dto.openai.response.ChatCompletionChunk
import com.vibe.app.data.dto.openai.response.ErrorDetail
import com.vibe.app.data.dto.openai.response.ResponseErrorEvent
import com.vibe.app.data.dto.openai.response.ResponsesStreamEvent
import com.vibe.app.data.dto.qwen.request.QwenChatCompletionRequest
import com.vibe.app.data.dto.qwen.response.QwenChatCompletionResponse
import com.vibe.app.feature.diagnostic.ChatDiagnosticLogger
import com.vibe.app.feature.diagnostic.ModelExecutionTrace
import com.vibe.app.feature.diagnostic.ModelRequestDiagnosticContext
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToJsonElement

@Singleton
class OpenAIAPIImpl @Inject constructor(
    private val networkClient: NetworkClient,
    private val diagnosticLogger: ChatDiagnosticLogger,
) : OpenAIAPI {

    private var token: String? = null

    private var apiUrl: String =
        OPENROUTER_API_URL

    private var providerType: String =
        PROVIDER_OPEN_ROUTER

    override fun setToken(
        token: String?
    ) {
        this.token =
            normalizeApiKey(
                token
            )
    }

    override fun setAPIUrl(
        url: String
    ) {
        apiUrl =
            normalizeBaseUrl(
                url
            )
    }

    override fun setProvider(
        type: String,
        customUrl: String?,
    ) {

        providerType =
            normalizeProviderType(
                type
            )

        val normalizedCustomUrl =
            customUrl
                ?.let(
                    ::normalizeBaseUrl
                )
                ?.takeIf {
                    it.isNotBlank()
                }

        apiUrl =
            when (providerType) {

                PROVIDER_OPEN_ROUTER ->
                    OPENROUTER_API_URL

                PROVIDER_GOOGLE_AI_STUDIO ->
                    normalizedCustomUrl
                        ?: GOOGLE_AI_STUDIO_API_URL

                PROVIDER_CUSTOM ->
                    normalizedCustomUrl
                        ?: ""

                else ->
                    normalizedCustomUrl
                        ?: apiUrl
            }
    }

    override suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean,
    ): List<OpenRouterModel> {

        val normalizedApiKey =
            normalizeApiKey(
                apiKey
            )
                ?: return emptyList()

        val endpoint =
            "$OPENROUTER_API_URL/v1/models"

        return try {

            val response: String =
                networkClient()
                    .get(
                        endpoint
                    ) {

                        header(
                            "Authorization",
                            "Bearer $normalizedApiKey",
                        )

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

            val parsedResponse =
                NetworkClient.json
                    .decodeFromString<OpenRouterModelsResponse>(
                        response
                    )

            if (isFreeOnly) {

                parsedResponse.data
                    .filter {
                        it.pricing?.isFree == true
                    }
                    .sortedBy {
                        it.name ?: it.id
                    }

            } else {

                parsedResponse.data
                    .filter {
                        it.pricing?.isFree == false
                    }
                    .sortedBy {
                        it.pricing?.averagePrice
                            ?: Double.MAX_VALUE
                    }
            }

        } catch (
            _: Exception
        ) {

            emptyList()
        }
    }

    override fun streamQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext:
            ModelRequestDiagnosticContext?,
        trace:
            ModelExecutionTrace?,
    ): Flow<ChatCompletionChunk> {

        /*
         * Capture provider configuration now.
         *
         * OpenAIAPIImpl is a Singleton and its provider
         * configuration is mutable.
         *
         * Capturing it here prevents another request
         * from changing this request while the Flow
         * is already running.
         */
        val requestProvider =
            providerType

        val requestApiUrl =
            apiUrl

        val requestToken =
            token

        val endpoint =
            buildChatCompletionsEndpoint(
                provider =
                    requestProvider,

                baseUrl =
                    requestApiUrl
            )

        /*
         * The request is serialized exactly as received.
         *
         * The selected model is therefore preserved.
         * No model fallback is added here.
         */
        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(
                    request
                )
                .toString()

        return flow {

            try {

                networkClient()
                    .preparePost(
                        endpoint
                    ) {

                        contentType(
                            ContentType.Application.Json
                        )

                        accept(
                            ContentType.Text.EventStream
                        )

                        setBody(
                            requestBody
                        )

                        /*
                         * Custom API is allowed to have
                         * no API key.
                         */
                        requestToken?.let {
                            bearerAuth(
                                it
                            )
                        }

                        applyProviderHeaders(
                            request =
                                this,

                            provider =
                                requestProvider
                        )
                    }
                    .execute { response ->

                        if (
                            !response.status
                                .isSuccess()
                        ) {

                            emit(
                                ChatCompletionChunk(
                                    error =
                                        ErrorDetail(
                                            message =
                                                response.body<String>(),

                                            type =
                                                "http_error",

                                            code =
                                                response.status
                                                    .value
                                                    .toString()
                                        )
                                )
                            )

                            return@execute
                        }

                        consumeChatCompletionStream(
                            response =
                                response,

                            collector =
                                this@flow
                        )
                    }

            } catch (
                e: Exception
            ) {

                emit(
                    ChatCompletionChunk(
                        error =
                            ErrorDetail(
                                message =
                                    e.message
                                        ?: "Unknown network error",

                                type =
                                    "network_error"
                            )
                    )
                )
            }
        }
    }

    override suspend fun completeQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext:
            ModelRequestDiagnosticContext?,
        trace:
            ModelExecutionTrace?,
    ): QwenChatCompletionResponse {

        val requestProvider =
            providerType

        val requestApiUrl =
            apiUrl

        val requestToken =
            token

        val endpoint =
            buildChatCompletionsEndpoint(
                provider =
                    requestProvider,

                baseUrl =
                    requestApiUrl
            )

        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(
                    request
                )
                .toString()

        return try {

            networkClient()
                .preparePost(
                    endpoint
                ) {

                    contentType(
                        ContentType.Application.Json
                    )

                    accept(
                        ContentType.Application.Json
                    )

                    setBody(
                        requestBody
                    )

                    requestToken?.let {
                        bearerAuth(
                            it
                        )
                    }

                    applyProviderHeaders(
                        request =
                            this,

                        provider =
                            requestProvider
                    )
                }
                .execute { response ->

                    val body =
                        response.body<String>()

                    if (
                        !response.status
                            .isSuccess()
                    ) {

                        return@execute QwenChatCompletionResponse(
                            error =
                                com.vibe.app.data.dto.qwen.response.QwenErrorDetail(
                                    message =
                                        body,

                                    code =
                                        response.status
                                            .value
                                            .toString()
                                )
                        )
                    }

                    NetworkClient.openAIJson
                        .decodeFromString<QwenChatCompletionResponse>(
                            body
                        )
                }

        } catch (
            e: Exception
        ) {

            QwenChatCompletionResponse(
                error =
                    com.vibe.app.data.dto.qwen.response.QwenErrorDetail(
                        message =
                            e.message
                                ?: "Unknown error",

                        code =
                            "network_error"
                    )
            )
        }
    }

    override fun streamChatCompletion(
        request: ChatCompletionRequest,
        diagnosticContext:
            ModelRequestDiagnosticContext?,
        trace:
            ModelExecutionTrace?,
    ): Flow<ChatCompletionChunk> {

        val requestProvider =
            providerType

        val requestApiUrl =
            apiUrl

        val requestToken =
            token

        val endpoint =
            buildChatCompletionsEndpoint(
                provider =
                    requestProvider,

                baseUrl =
                    requestApiUrl
            )

        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(
                    request
                )
                .toString()

        return flow {

            try {

                networkClient()
                    .preparePost(
                        endpoint
                    ) {

                        contentType(
                            ContentType.Application.Json
                        )

                        accept(
                            ContentType.Text.EventStream
                        )

                        setBody(
                            requestBody
                        )

                        requestToken?.let {
                            bearerAuth(
                                it
                            )
                        }

                        applyProviderHeaders(
                            request =
                                this,

                            provider =
                                requestProvider
                        )
                    }
                    .execute { response ->

                        if (
                            !response.status
                                .isSuccess()
                        ) {

                            emit(
                                ChatCompletionChunk(
                                    error =
                                        ErrorDetail(
                                            message =
                                                response.body<String>(),

                                            type =
                                                "http_error",

                                            code =
                                                response.status
                                                    .value
                                                    .toString()
                                        )
                                )
                            )

                            return@execute
                        }

                        consumeChatCompletionStream(
                            response =
                                response,

                            collector =
                                this@flow
                        )
                    }

            } catch (
                e: Exception
            ) {

                emit(
                    ChatCompletionChunk(
                        error =
                            ErrorDetail(
                                message =
                                    e.message
                                        ?: "Unknown network error",

                                type =
                                    "network_error"
                            )
                    )
                )
            }
        }
    }

    override fun streamResponses(
        request: ResponsesRequest,
        diagnosticContext:
            ModelRequestDiagnosticContext?,
        trace:
            ModelExecutionTrace?,
    ): Flow<ResponsesStreamEvent> {

        val requestProvider =
            providerType

        val requestApiUrl =
            apiUrl

        val requestToken =
            token

        val endpoint =
            buildResponsesEndpoint(
                baseUrl =
                    requestApiUrl
            )

        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(
                    request
                )
                .toString()

        return flow {

            try {

                networkClient()
                    .preparePost(
                        endpoint
                    ) {

                        contentType(
                            ContentType.Application.Json
                        )

                        accept(
                            ContentType.Text.EventStream
                        )

                        setBody(
                            requestBody
                        )

                        requestToken?.let {
                            bearerAuth(
                                it
                            )
                        }

                        applyProviderHeaders(
                            request =
                                this,

                            provider =
                                requestProvider
                        )
                    }
                    .execute { response ->

                        if (
                            !response.status
                                .isSuccess()
                        ) {

                            emit(
                                ResponseErrorEvent(
                                    message =
                                        response.body<String>(),

                                    code =
                                        response.status
                                            .value
                                            .toString()
                                )
                            )

                            return@execute
                        }

                        consumeResponsesStream(
                            response =
                                response,

                            collector =
                                this@flow
                        )
                    }

            } catch (
                e: Exception
            ) {

                emit(
                    ResponseErrorEvent(
                        message =
                            e.message
                                ?: "Unknown error",

                        code =
                            "network_error"
                    )
                )
            }
        }
    }

    /*
     * Shared Chat Completions SSE parser.
     *
     * Used for:
     *
     * OpenRouter
     * Google AI Studio
     * Custom OpenAI-compatible APIs
     */
    private suspend fun consumeChatCompletionStream(
        response: HttpResponse,
        collector:
            FlowCollector<ChatCompletionChunk>,
    ) {

        val channel =
            response.bodyAsChannel()

        val eventLines =
            mutableListOf<String>()

        while (
            !channel.isClosedForRead
        ) {

            val line =
                channel.readUTF8Line()
                    ?: break

            if (
                line.isBlank()
            ) {

                collector
                    .handleChatCompletionSseEvent(
                        eventLines
                    )

                eventLines.clear()

            } else {

                eventLines +=
                    line
            }
        }

        if (
            eventLines.isNotEmpty()
        ) {

            collector
                .handleChatCompletionSseEvent(
                    eventLines
                )
        }
    }

    private suspend fun consumeResponsesStream(
        response: HttpResponse,
        collector:
            FlowCollector<ResponsesStreamEvent>,
    ) {

        val channel =
            response.bodyAsChannel()

        while (
            !channel.isClosedForRead
        ) {

            val line =
                channel.readUTF8Line()
                    ?: break

            if (
                line.isBlank()
            ) {
                continue
            }

            if (
                !line.startsWith(
                    "data:"
                )
            ) {
                continue
            }

            val data =
                line
                    .removePrefix(
                        "data:"
                    )
                    .trim()

            if (
                data.isBlank() ||
                data == "[DONE]"
            ) {
                continue
            }

            try {

                val event =
                    NetworkClient.openAIJson
                        .decodeFromString<ResponsesStreamEvent>(
                            data
                        )

                collector.emit(
                    event
                )

            } catch (
                _: Exception
            ) {
                /*
                 * Ignore SSE event types that are not
                 * represented by the current DTO.
                 */
            }
        }
    }

    private suspend fun FlowCollector<ChatCompletionChunk>
        .handleChatCompletionSseEvent(
            eventLines: List<String>,
        ) {

        val data =
            eventLines
                .filter {
                    it.startsWith(
                        "data:"
                    )
                }
                .joinToString(
                    "\n"
                ) {
                    it.removePrefix(
                        "data:"
                    )
                        .trim()
                }

        if (
            data.isBlank() ||
            data == "[DONE]"
        ) {
            return
        }

        try {

            val chunk =
                NetworkClient.openAIJson
                    .decodeFromString<ChatCompletionChunk>(
                        data
                    )

            emit(
                chunk
            )

        } catch (
            e: Exception
        ) {

            emit(
                ChatCompletionChunk(
                    error =
                        ErrorDetail(
                            message =
                                e.message
                                    ?: "Failed parsing SSE event",

                            type =
                                "parse_error",

                            code =
                                "invalid_stream_chunk"
                        )
                )
            )
        }
    }

    /*
     * =========================================================
     * CHAT COMPLETIONS ENDPOINT
     * =========================================================
     */
    private fun buildChatCompletionsEndpoint(
        provider: String,
        baseUrl: String,
    ): String {

        val base =
            normalizeBaseUrl(
                baseUrl
            )

        return when (provider) {

            /*
             * Google base:
             *
             * https://generativelanguage.googleapis.com/v1beta/openai
             *
             * Final:
             *
             * .../openai/chat/completions
             */
            PROVIDER_GOOGLE_AI_STUDIO -> {

                if (
                    base.endsWith(
                        "/chat/completions"
                    )
                ) {

                    base

                } else {

                    "$base/chat/completions"
                }
            }

            /*
             * OpenRouter:
             *
             * https://openrouter.ai/api
             *
             * becomes:
             *
             * https://openrouter.ai/api/v1/chat/completions
             *
             * Custom uses the same OpenAI-compatible
             * normalization.
             */
            PROVIDER_OPEN_ROUTER,
            PROVIDER_CUSTOM ->

                buildOpenAICompatibleChatEndpoint(
                    base
                )

            else ->

                buildOpenAICompatibleChatEndpoint(
                    base
                )
        }
    }

    /*
     * Supports all of these:
     *
     * https://example.com
     * ->
     * https://example.com/v1/chat/completions
     *
     * https://example.com/v1
     * ->
     * https://example.com/v1/chat/completions
     *
     * https://example.com/v1/chat/completions
     * ->
     * unchanged
     *
     * Therefore /v1 is NEVER duplicated.
     */
    private fun buildOpenAICompatibleChatEndpoint(
        baseUrl: String,
    ): String {

        val base =
            normalizeBaseUrl(
                baseUrl
            )

        return when {

            base.endsWith(
                "/chat/completions"
            ) ->
                base

            base.endsWith(
                "/v1"
            ) ->
                "$base/chat/completions"

            else ->
                "$base/v1/chat/completions"
        }
    }

    /*
     * Kept for the existing Responses API path.
     */
    private fun buildResponsesEndpoint(
        baseUrl: String,
    ): String {

        val base =
            normalizeBaseUrl(
                baseUrl
            )

        return when {

            base.endsWith(
                "/responses"
            ) ->
                base

            base.endsWith(
                "/v1"
            ) ->
                "$base/responses"

            else ->
                "$base/v1/responses"
        }
    }

    private fun applyProviderHeaders(
        request:
            io.ktor.client.request.HttpRequestBuilder,

        provider: String,
    ) {

        when (provider) {

            PROVIDER_OPEN_ROUTER -> {

                request.header(
                    "HTTP-Referer",
                    APP_REFERER
                )

                request.header(
                    "X-Title",
                    APP_TITLE
                )
            }

            PROVIDER_GOOGLE_AI_STUDIO -> {

                request.header(
                    "x-goog-api-client",
                    "vibe-app/1.0"
                )
            }
        }
    }

    /*
     * Normalize old provider names into the
     * three canonical values used by the app.
     */
    private fun normalizeProviderType(
        rawType: String,
    ): String {

        return when (
            rawType
                .trim()
                .uppercase()
        ) {

            "OPEN_ROUTER",
            "OPENROUTER" ->
                PROVIDER_OPEN_ROUTER

            "GOOGLE_AI_STUDIO",
            "GOOGLE",
            "GEMINI" ->
                PROVIDER_GOOGLE_AI_STUDIO

            "CUSTOM" ->
                PROVIDER_CUSTOM

            else ->
                rawType
                    .trim()
                    .uppercase()
        }
    }

    /*
     * Accept:
     *
     * sk-...
     *
     * Bearer sk-...
     *
     * bearer sk-...
     *
     * Store/use internally without Bearer.
     */
    private fun normalizeApiKey(
        rawApiKey: String?,
    ): String? {

        val trimmed =
            rawApiKey
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val normalized =
            if (
                trimmed.startsWith(
                    prefix = "Bearer ",
                    ignoreCase = true,
                )
            ) {

                trimmed
                    .substring(
                        "Bearer ".length
                    )
                    .trim()

            } else {

                trimmed
            }

        return normalized
            .takeIf {
                it.isNotBlank()
            }
    }

    private fun normalizeBaseUrl(
        rawUrl: String,
    ): String {

        return rawUrl
            .trim()
            .trimEnd('/')
    }

    companion object {

        private const val PROVIDER_OPEN_ROUTER =
            "OPEN_ROUTER"

        private const val PROVIDER_GOOGLE_AI_STUDIO =
            "GOOGLE_AI_STUDIO"

        private const val PROVIDER_CUSTOM =
            "CUSTOM"

        /*
         * OpenRouter base.
         *
         * Final Chat Completions endpoint:
         *
         * https://openrouter.ai/api/v1/chat/completions
         */
        private const val OPENROUTER_API_URL =
            "https://openrouter.ai/api"

        /*
         * Google OpenAI-compatible base.
         *
         * Final Chat Completions endpoint:
         *
         * https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
         */
        private const val GOOGLE_AI_STUDIO_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai"

        private const val APP_REFERER =
            "https://vibe.app"

        private const val APP_TITLE =
            "Vibe App"
    }
}
