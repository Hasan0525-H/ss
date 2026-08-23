package com.vibe.app.data.network

import com.vibe.app.data.dto.openai.request.ChatCompletionRequest
import com.vibe.app.data.dto.openai.request.ResponsesRequest
import com.vibe.app.data.dto.openai.response.ChatCompletionChunk
import com.vibe.app.data.dto.openai.response.ErrorDetail
import com.vibe.app.data.dto.openai.response.ResponseErrorEvent
import com.vibe.app.data.dto.openai.response.ResponsesStreamEvent
import com.vibe.app.data.dto.openai.response.UnknownEvent
import com.vibe.app.feature.diagnostic.ChatDiagnosticLogger
import com.vibe.app.feature.diagnostic.ModelExecutionTrace
import com.vibe.app.feature.diagnostic.ModelRequestDiagnosticContext
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
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
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.encodeToJsonElement

class OpenAIAPIImpl @Inject constructor(
    private val networkClient: NetworkClient,
    private val diagnosticLogger: ChatDiagnosticLogger,
) : OpenAIAPI {

    private var token: String? = null

    private var apiUrl: String =
        "https://openrouter.ai/api/"

    private var providerType: String =
        "OPEN_ROUTER"


    override fun setToken(token: String?) {
        this.token = token
    }


    override fun setAPIUrl(url: String) {
        this.apiUrl = url
    }


    override fun setProvider(
        type: String,
        customUrl: String?
    ) {

        providerType = type

        apiUrl = when (type) {

            "OPEN_ROUTER" ->
                "https://openrouter.ai/api/"

            "CUSTOM" ->
                customUrl ?: ""

            else ->
                "https://openrouter.ai/api/"
        }
    }


    private fun buildEndpoint(
        path: String
    ): String {

        return if (apiUrl.endsWith("/")) {
            "${apiUrl.removeSuffix("/")}$path"
        } else {
            "$apiUrl$path"
        }
    }


    private fun applyProviderHeaders(
        request: io.ktor.client.request.HttpRequestBuilder
    ) {

        if (providerType == "OPEN_ROUTER") {

            request.header(
                "HTTP-Referer",
                "https://vibe.app"
            )

            request.header(
                "X-Title",
                "Vibe App"
            )
        }
    }


    override fun streamChatCompletion(
        request: ChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext?,
        trace: ModelExecutionTrace?,
    ): Flow<ChatCompletionChunk> = flow {

        val endpoint =
            buildEndpoint("/v1/chat/completions")


        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(request)
                .toString()


        try {

            networkClient
                .preparePost(endpoint) {

                    contentType(ContentType.Application.Json)

                    setBody(requestBody)

                    accept(ContentType.Text.EventStream)


                    token?.let {
                        bearerAuth(it)
                    }


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


                    val channel =
                        response.bodyAsChannel()

                    val eventLines =
                        mutableListOf<String>()


                    while (!channel.isClosedForRead) {

                        val line =
                            channel.readUTF8Line()
                                ?: break


                        if (line.isBlank()) {

                            val stop =
                                handleChatCompletionSseEvent(
                                    endpoint,
                                    eventLines
                                ) {
                                    emit(it)
                                }

                            eventLines.clear()

                            if (stop) break

                        } else {

                            eventLines += line

                        }
                    }


                    if (eventLines.isNotEmpty()) {

                        handleChatCompletionSseEvent(
                            endpoint,
                            eventLines
                        ) {
                            emit(it)
                        }
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
    }    override fun streamResponses(
        request: ResponsesRequest,
        diagnosticContext: ModelRequestDiagnosticContext?,
        trace: ModelExecutionTrace?,
    ): Flow<ResponsesStreamEvent> = flow {

        val endpoint =
            buildEndpoint("/v1/responses")


        val requestBody =
            NetworkClient.openAIJson
                .encodeToJsonElement(request)
                .toString()


        try {

            networkClient
                .preparePost(endpoint) {

                    contentType(ContentType.Application.Json)

                    setBody(requestBody)

                    accept(ContentType.Text.EventStream)


                    token?.let {
                        bearerAuth(it)
                    }


                    applyProviderHeaders(this)

                }
                .execute { response ->


                    if (!response.status.isSuccess()) {

                        emit(
                            ResponseErrorEvent(
                                message = response.body<String>(),
                                code = response.status.value.toString()
                            )
                        )

                        return@execute
                    }


                    val channel =
                        response.bodyAsChannel()


                    val eventLines =
                        mutableListOf<String>()


                    while (!channel.isClosedForRead) {

                        val line =
                            channel.readUTF8Line()
                                ?: break


                        if (line.isBlank()) {

                            val stop =
                                handleResponsesSseEvent(
                                    endpoint,
                                    eventLines
                                ) {
                                    emit(it)
                                }


                            eventLines.clear()


                            if (stop) break


                        } else {

                            eventLines += line

                        }
                    }


                    if (eventLines.isNotEmpty()) {

                        handleResponsesSseEvent(
                            endpoint,
                            eventLines
                        ) {
                            emit(it)
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



    private suspend fun handleChatCompletionSseEvent(
        endpoint: String,
        lines: List<String>,
        emitEvent: suspend (ChatCompletionChunk) -> Unit,
    ): Boolean {

        if (lines.isEmpty()) return false


        val data =
            lines
                .filter { it.startsWith("data:") }
                .joinToString("\n") {
                    it.removePrefix("data:")
                        .trimStart()
                }
                .trim()


        if (data.isBlank()) return false


        if (data == "[DONE]") return true


        try {

            emitEvent(
                NetworkClient.openAIJson
                    .decodeFromString(data)
            )

        } catch (_: Exception) {

        }


        return false
    }



    private suspend fun handleResponsesSseEvent(
        endpoint: String,
        lines: List<String>,
        emitEvent: suspend (ResponsesStreamEvent) -> Unit,
    ): Boolean {

        if (lines.isEmpty()) return false


        val data =
            lines
                .filter { it.startsWith("data:") }
                .joinToString("\n") {
                    it.removePrefix("data:")
                        .trimStart()
                }
                .trim()


        if (data.isBlank()) return false


        if (data == "[DONE]") return true


        try {

            emitEvent(
                NetworkClient.openAIJson
                    .decodeFromString(data)
            )

        } catch (_: Exception) {

            emitEvent(UnknownEvent)

        }


        return false
    }

}
