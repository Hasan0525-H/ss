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
        "OPEN_ROUTER"

    override fun setToken(
        token: String?
    ) {
        this.token =
            token
                ?.trim()
                ?.removePrefix("Bearer ")
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
    }

    override fun setAPIUrl(
        url: String
    ) {
        this.apiUrl =
            url
                .trim()
                .trimEnd('/')
    }

    override fun setProvider(
        type: String,
        customUrl: String?
    ) {
        providerType =
            type
                .trim()
                .uppercase()

        apiUrl =
            when (providerType) {

                "OPEN_ROUTER",
                "OPENROUTER" ->
                    OPENROUTER_API_URL

                "GOOGLE_AI_STUDIO",
                "GOOGLE",
                "GEMINI" ->
                    GOOGLE_AI_STUDIO_API_URL

                "CUSTOM" ->
                    customUrl
                        ?.trim()
                        ?.trimEnd('/')
                        ?: ""

                else ->
                    customUrl
                        ?.trim()
                        ?.trimEnd('/')
                        ?: apiUrl
            }
    }

    override suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean
    ): List<OpenRouterModel> {

        val endpoint =
            "$OPENROUTER_API_URL/v1/models"

        return try {

            val response: String =
                networkClient()
                    .get(endpoint) {

                        header(
                            "Authorization",
                            if (
                                apiKey.startsWith(
                                    "Bearer ",
                                    ignoreCase = true
                                )
                            ) {
                                apiKey
                            } else {
                                "Bearer $apiKey"
                            }
                        )

                        applyProviderHeaders(this)
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
            e: Exception
        ) {

            emptyList()
        }
    }

    /*
     * Google AI Studio's OpenAI-compatible API is:
     *
     * https://generativelanguage.googleapis.com/v1beta/openai
     *
     * Unlike OpenRouter, it does NOT use:
     *
     * /v1/chat/completions
     *
     * after the base URL.
     *
     * The correct Google endpoint is:
     *
     * /chat/completions
     *
     * OpenRouter continues to use:
     *
     * /v1/chat/completions
     */
    private fun buildChatCompletionsEndpoint(): String {

        return when (providerType) {

            "GOOGLE_AI_STUDIO",
            "GOOGLE",
            "GEMINI" -> {

                "${apiUrl.trimEnd('/')}/chat/completions"
            }

            else -> {

                buildEndpoint(
                    "/v1/chat/completions"
                )
            }
        }
    }

    private fun buildResponsesEndpoint(): String {

        return buildEndpoint(
            "/v1/responses"
        )
    }

    private fun buildEndpoint(
        path: String
    ): String {

        val cleanPath =
            if (path.startsWith("/")) {
                path
            } else {
                "/$path"
            }

        return if (
            apiUrl.endsWith("/")
        ) {

            "${apiUrl.removeSuffix("/")}$cleanPath"

        } else {

            "$apiUrl$cleanPath"
        }
    }

    private fun applyProviderHeaders(
        request:
            io.ktor.client.request.HttpRequestBuilder
    ) {

        when (providerType) {

            "OPEN_ROUTER",
            "OPENROUTER" -> {

                request.header(
                    "HTTP-Referer",
                    "https://vibe.app"
                )

                request.header(
                    "X-Title",
                    "Vibe App"
                )
            }

            "GOOGLE_AI_STUDIO",
            "GOOGLE",
            "GEMINI" -> {

                request.header(
                    "x-goog-api-client",
                    "vibe-app/1.0"
                )
            }
        }
    }

    override fun streamQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext:
            ModelRequestDiagnosticContext?,
        trace:
            ModelExecutionTrace?,
    ): Flow<ChatCompletionChunk> = flow {

        val endpoint =
            buildChatCompletionsEndpoint()

        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(request)
                .toString()

        try {

            networkClient()
                .preparePost(endpoint) {

                    contentType(
                        ContentType.Application.Json
                    )

                    setBody(
                        requestBody
                    )

                    accept(
                        ContentType.Text.EventStream
                    )

                    token?.let {
                        bearerAuth(it)
                    }

                    applyProviderHeaders(this)

                }
                .execute { response ->

                    if (
                        !response.status.isSuccess()
                    ) {

                        emit(
                            ChatCompletionChunk(
                                error =
                                    ErrorDetail(
                                        message =
                                            response
                                                .body<String>(),

                                        type =
                                            "http_error",

                                        code =
                                            response
                                                .status
                                                .value
                                                .toString()
                                    )
                            )
                        )

                        return@execute
                    }

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

                            handleChatCompletionSseEvent(
                                endpoint,
                                eventLines
                            )

                            eventLines.clear()

                        } else {

                            eventLines += line
                        }
                    }

                    if (
                        eventLines.isNotEmpty()
                    ) {

                        handleChatCompletionSseEvent(
                            endpoint,
                            eventLines
                        )
                    }
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

    override suspend fun completeQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext:
            ModelRequestDiagnosticContext?,
        trace:
            ModelExecutionTrace?,
    ): QwenChatCompletionResponse {

        val endpoint =
            buildChatCompletionsEndpoint()

        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(request)
                .toString()

        return try {

            networkClient()
                .preparePost(endpoint) {

                    contentType(
                        ContentType.Application.Json
                    )

                    accept(
                        ContentType.Application.Json
                    )

                    setBody(
                        requestBody
                    )

                    token?.let {
                        bearerAuth(it)
                    }

                    applyProviderHeaders(this)

                }
                .execute { response ->

                    val body =
                        response.body<String>()

                    if (
                        !response.status.isSuccess()
                    ) {

                        return@execute QwenChatCompletionResponse(
                            error =
                                com.vibe.app.data.dto.qwen.response.QwenErrorDetail(
                                    message =
                                        body,

                                    code =
                                        response
                                            .status
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
    ): Flow<ChatCompletionChunk> = flow {

        val endpoint =
            buildChatCompletionsEndpoint()

        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(request)
                .toString()

        try {

            networkClient()
                .preparePost(endpoint) {

                    contentType(
                        ContentType.Application.Json
                    )

                    setBody(
                        requestBody
                    )

                    accept(
                        ContentType.Text.EventStream
                    )

                    token?.let {
                        bearerAuth(it)
                    }

                    applyProviderHeaders(this)

                }
                .execute { response ->

                    if (
                        !response.status.isSuccess()
                    ) {

                        emit(
                            ChatCompletionChunk(
                                error =
                                    ErrorDetail(
                                        message =
                                            response
                                                .body<String>(),

                                        type =
                                            "http_error",

                                        code =
                                            response
                                                .status
                                                .value
                                                .toString()
                                    )
                            )
                        )

                        return@execute
                    }

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

                            handleChatCompletionSseEvent(
                                endpoint,
                                eventLines
                            )

                            eventLines.clear()

                        } else {

                            eventLines += line
                        }
                    }

                    if (
                        eventLines.isNotEmpty()
                    ) {

                        handleChatCompletionSseEvent(
                            endpoint,
                            eventLines
                        )
                    }
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

    override fun streamResponses(
        request: ResponsesRequest,
        diagnosticContext:
            ModelRequestDiagnosticContext?,
        trace:
            ModelExecutionTrace?,
    ): Flow<ResponsesStreamEvent> = flow {

        val endpoint =
            buildResponsesEndpoint()

        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(request)
                .toString()

        try {

            networkClient()
                .preparePost(endpoint) {

                    contentType(
                        ContentType.Application.Json
                    )

                    setBody(
                        requestBody
                    )

                    accept(
                        ContentType.Text.EventStream
                    )

                    token?.let {
                        bearerAuth(it)
                    }

                    applyProviderHeaders(this)
                }
                .execute { response ->

                    if (
                        !response.status.isSuccess()
                    ) {

                        emit(
                            ResponseErrorEvent(
                                message =
                                    response
                                        .body<String>(),

                                code =
                                    response
                                        .status
                                        .value
                                        .toString()
                            )
                        )

                        return@execute
                    }

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
                            line.startsWith(
                                "data:"
                            )
                        ) {

                            val data =
                                line
                                    .removePrefix(
                                        "data:"
                                    )
                                    .trim()

                            if (
                                data.isNotBlank() &&
                                data != "[DONE]"
                            ) {

                                try {

                                    val event =
                                        NetworkClient
                                            .openAIJson
                                            .decodeFromString<ResponsesStreamEvent>(
                                                data
                                            )

                                    emit(event)

                                } catch (_: Exception) {
                                    /*
                                     * Ignore unsupported
                                     * response events.
                                     */
                                }
                            }
                        }
                    }
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

    private suspend fun FlowCollector<ChatCompletionChunk>
        .handleChatCompletionSseEvent(
            endpoint: String,
            eventLines: List<String>
        ) {

        val data =
            eventLines
                .filter {
                    it.startsWith("data:")
                }
                .joinToString("\n") {
                    it.removePrefix("data:")
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

            emit(chunk)

        } catch (
            e: Exception
        ) {

            try {

                if (
                    data.contains(
                        "tool_calls"
                    )
                ) {

                    val fixedData =
                        data.replace(
                            "\"tool_calls\"",
                            "\"delta\":{\"tool_calls\""
                        )

                    try {

                        val fallbackChunk =
                            NetworkClient.openAIJson
                                .decodeFromString<ChatCompletionChunk>(
                                    fixedData
                                )

                        emit(
                            fallbackChunk
                        )

                    } catch (_: Exception) {

                        emit(
                            ChatCompletionChunk(
                                error =
                                    ErrorDetail(
                                        message =
                                            e.message
                                                ?: "Failed parsing tool call event",

                                        type =
                                            "parse_error",

                                        code =
                                            "invalid_tool_stream_chunk"
                                    )
                            )
                        )
                    }

                } else {

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

            } catch (
                innerEx: Exception
            ) {

                emit(
                    ChatCompletionChunk(
                        error =
                            ErrorDetail(
                                message =
                                    innerEx.message
                                        ?: "Failed parsing stream event",

                                type =
                                    "parse_error",

                                code =
                                    "invalid_stream_chunk"
                            )
                    )
                )
            }
        }
    }

    companion object {

        private const val OPENROUTER_API_URL =
            "https://openrouter.ai/api"

        /*
         * Google AI Studio official OpenAI-compatible
         * Gemini endpoint.
         *
         * IMPORTANT:
         *
         * Google endpoint:
         * /v1beta/openai/chat/completions
         *
         * OpenRouter endpoint:
         * /api/v1/chat/completions
         */
        private const val GOOGLE_AI_STUDIO_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai"
    }
}
