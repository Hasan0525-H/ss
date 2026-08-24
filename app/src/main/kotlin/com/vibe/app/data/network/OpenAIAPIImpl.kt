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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToJsonElement

class OpenAIAPIImpl @Inject constructor(
    private val networkClient: NetworkClient,
    private val diagnosticLogger: ChatDiagnosticLogger,
) : OpenAIAPI {

    private var token: String? = null
    private var apiUrl: String = "https://openrouter.ai/api/"
    private var providerType: String = "OPEN_ROUTER"

    override fun setToken(token: String?) {
        this.token = token
    }

    override fun setAPIUrl(url: String) {
        this.apiUrl = url
    }

    override fun setProvider(type: String, customUrl: String?) {
        providerType = type.uppercase()
        apiUrl = when (providerType) {
            "OPEN_ROUTER", "OPENROUTER" -> "https://openrouter.ai/api/"
            "CUSTOM" -> customUrl?.trim()?.trimEnd('/') ?: ""
            else -> customUrl?.trim()?.trimEnd('/') ?: "https://openrouter.ai/api/"
        }
    }

    override suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean
    ): List<OpenRouterModel> {
        val endpoint = "https://openrouter.ai/api/v1/models"
        return try {
            val response: String = networkClient().get(endpoint) {
                header("Authorization", "Bearer $apiKey")
                applyProviderHeaders(this)
            }.body()

            val parsedResponse = NetworkClient.json.decodeFromString<OpenRouterModelsResponse>(response)
            if (isFreeOnly) {
                parsedResponse.data.filter { it.pricing?.isFree == true }
            } else {
                parsedResponse.data
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildEndpoint(path: String): String {
        return if (apiUrl.endsWith("/")) {
            "${apiUrl.removeSuffix("/")}$path"
        } else {
            "$apiUrl$path"
        }
    }

    private fun applyProviderHeaders(request: io.ktor.client.request.HttpRequestBuilder) {
        if (providerType == "OPEN_ROUTER" || providerType == "OPENROUTER") {
            request.header("HTTP-Referer", "https://vibe.app")
            request.header("X-Title", "Vibe App")
        }
    }

    override fun streamQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext?,
        trace: ModelExecutionTrace?,
    ): Flow<ChatCompletionChunk> = flow {
        val endpoint = buildEndpoint("/v1/chat/completions")
        val requestBody = NetworkClient.json.encodeToJsonElement(request).toString()

        try {
            networkClient()
                .preparePost(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                    accept(ContentType.Text.EventStream)
                    token?.let { bearerAuth(it) }
                    applyProviderHeaders(this)
                }
                .execute { response ->
                    if (!response.status.isSuccess()) {
                        emit(
                            ChatCompletionChunk(
                                error = ErrorDetail(
                                    message = response.body<String>(),
                                    type = "http_error",
                                    code = response.status.value.toString()
                                )
                            )
                        )
                        return@execute
                    }

                    val channel = response.bodyAsChannel()
                    val eventLines = mutableListOf<String>()

                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.isBlank()) {
                            handleChatCompletionSseEvent(endpoint, eventLines)
                            eventLines.clear()
                        } else {
                            eventLines += line
                        }
                    }

                    if (eventLines.isNotEmpty()) {
                        handleChatCompletionSseEvent(endpoint, eventLines)
                    }
                }
        } catch (e: Exception) {
            emit(
                ChatCompletionChunk(
                    error = ErrorDetail(
                        message = e.message ?: "Unknown network error",
                        type = "network_error"
                    )
                )
            )
        }
    }

    override suspend fun completeQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext?,
        trace: ModelExecutionTrace?,
    ): QwenChatCompletionResponse {
        val endpoint = buildEndpoint("/v1/chat/completions")
        val requestBody = NetworkClient.json.encodeToJsonElement(request).toString()

        return try {
            networkClient()
                .preparePost(endpoint) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(requestBody)
                    token?.let { bearerAuth(it) }
                    applyProviderHeaders(this)
                }
                .execute { response ->
                    val body = response.body<String>()
                    if (!response.status.isSuccess()) {
                        return@execute QwenChatCompletionResponse(
                            error = com.vibe.app.data.dto.qwen.response.QwenErrorDetail(
                                message = body,
                                code = response.status.value.toString()
                            )
                        )
                    }
                    NetworkClient.json.decodeFromString<QwenChatCompletionResponse>(body)
                }
        } catch (e: Exception) {
            QwenChatCompletionResponse(
                error = com.vibe.app.data.dto.qwen.response.QwenErrorDetail(
                    message = e.message ?: "Unknown error",
                    code = "network_error"
                )
            )
        }
    }

    override fun streamChatCompletion(
        request: ChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext?,
        trace: ModelExecutionTrace?,
    ): Flow<ChatCompletionChunk> = flow {
        val endpoint = buildEndpoint("/v1/chat/completions")
        val requestBody = NetworkClient.openAIJson.encodeToJsonElement(request).toString()

        try {
            networkClient()
                .preparePost(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                    accept(ContentType.Text.EventStream)
                    token?.let { bearerAuth(it) }
                    applyProviderHeaders(this)
                }
                .execute { response ->
                    if (!response.status.isSuccess()) {
                        emit(
                            ChatCompletionChunk(
                                error = ErrorDetail(
                                    message = response.body<String>(),
                                    type = "http_error",
                                    code = response.status.value.toString()
                                )
                            )
                        )
                        return@execute
                    }

                    val channel = response.bodyAsChannel()
                    val eventLines = mutableListOf<String>()

                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.isBlank()) {
                            handleChatCompletionSseEvent(endpoint, eventLines)
                            eventLines.clear()
                        } else {
                            eventLines += line
                        }
                    }

                    if (eventLines.isNotEmpty()) {
                        handleChatCompletionSseEvent(endpoint, eventLines)
                    }
                }
        } catch (e: Exception) {
            emit(
                ChatCompletionChunk(
                    error = ErrorDetail(
                        message = e.message ?: "Unknown error",
                        type = "network_error"
                    )
                )
            )
        }
    }

    override fun streamResponses(
        request: ResponsesRequest,
        diagnosticContext: ModelRequestDiagnosticContext?,
        trace: ModelExecutionTrace?,
    ): Flow<ResponsesStreamEvent> = flow {
        val endpoint = buildEndpoint("/v1/responses")
        val requestBody = NetworkClient.openAIJson.encodeToJsonElement(request).toString()

        try {
            networkClient()
                .preparePost(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                    accept(ContentType.Text.EventStream)
                    token?.let { bearerAuth(it) }
                    applyProviderHeaders(this)
                }
                .execute { response ->
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.isBlank()) {
                            continue
                        }
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data.isNotBlank() && data != "[DONE]") {
                                try {
                                    val event = NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(data)
                                    emit(event)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            emit(
                ResponseErrorEvent(
                    message = e.message ?: "Unknown error",
                    code = "network_error"
                )
            )
        }
    }

    private suspend fun FlowCollector<ChatCompletionChunk>.handleChatCompletionSseEvent(
        endpoint: String,
        eventLines: List<String>,
    ) {
        val data = eventLines
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trim() }

        if (data.isBlank() || data == "[DONE]") {
            return
        }

        try {
            val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(data)
            emit(chunk)
        } catch (e: Exception) {
            emit(
                ChatCompletionChunk(
                    error = ErrorDetail(
                        message = e.message ?: "Failed parsing SSE event",
                        type = "parse_error",
                        code = "invalid_stream_chunk"
                    )
                )
            )
        }
    }
}
