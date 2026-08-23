package com.vibe.app.data.network

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



    fun setProvider(
        type: String,
        customUrl: String? = null
    ) {

        providerType = type


        apiUrl = when(type) {

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
        override fun streamQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext?,
        trace: ModelExecutionTrace?,
    ): Flow<ChatCompletionChunk> = flow {


        val endpoint =
            buildEndpoint("/v1/chat/completions")


        val requestBody =
            NetworkClient.json
                .encodeToJsonElement(request)
                .toString()



        try {


            networkClient()
                .preparePost(endpoint) {


                    contentType(
                        ContentType.Application.Json
                    )


                    setBody(requestBody)


                    accept(
                        ContentType.Text.EventStream
                    )


                    token?.let {

                        bearerAuth(it)

                    }


                    applyProviderHeaders(this)


                }
                .execute { response ->



                    if (!response.status.isSuccess()) {


                        val errorBody =
                            response.body<String>()



                        emit(

                            ChatCompletionChunk(

                                error =
                                    ErrorDetail(

                                        message = errorBody,

                                        type = "http_error",

                                        code =
                                            response.status.value.toString()

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


                            handleChatCompletionSseEvent(
                                endpoint,
                                eventLines
                            ) {

                                emit(it)

                            }



                            eventLines.clear()



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

                    error =

                        ErrorDetail(

                            message =
                                e.message
                                    ?: "Unknown network error",

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


        val endpoint =
            buildEndpoint("/v1/chat/completions")



        val requestBody =
            NetworkClient.json
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


                    setBody(requestBody)



                    token?.let {

                        bearerAuth(it)

                    }



                    applyProviderHeaders(this)


                }
                .execute { response ->


                    val body =
                        response.body<String>()


                    if (!response.status.isSuccess()) {


                        return@execute QwenChatCompletionResponse(

                            error =
                                com.vibe.app.data.dto.qwen.response.QwenErrorDetail(

                                    message = body,

                                    code =
                                        response.status.value.toString()

                                )

                        )

                    }



                    NetworkClient.json
                        .decodeFromString<QwenChatCompletionResponse>(
                            body
                        )

                }


        } catch (e: Exception) {


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


            networkClient()
                .preparePost(endpoint) {


                    contentType(
                        ContentType.Application.Json
                    )


                    setBody(requestBody)


                    accept(
                        ContentType.Text.EventStream
                    )


                    token?.let {

                        bearerAuth(it)

                    }


                    applyProviderHeaders(this)


                }
                .execute { response ->



                    if (!response.status.isSuccess()) {


                        emit(

                            ChatCompletionChunk(

                                error =
                                    ErrorDetail(

                                        message =
                                            response.body<String>(),

                                        type =
                                            "http_error",

                                        code =
                                            response.status.value.toString()

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


                            handleChatCompletionSseEvent(
                                endpoint,
                                eventLines
                            ) {

                                emit(it)

                            }



                            eventLines.clear()


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

                    error =
                        ErrorDetail(

                            message =
                                e.message
                                    ?: "Unknown error",

                            type =
                                "network_error"

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


        val endpoint =
            buildEndpoint("/v1/responses")



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


                    setBody(requestBody)


                    accept(
                        ContentType.Text.EventStream
                    )


                    token?.let {

                        bearerAuth(it)

                    }


                    applyProviderHeaders(this)

                }
                .execute { response ->


                    if (!response.status.isSuccess()) {


                        emit(

                            ResponseErrorEvent(

                                message =
                                    response.body<String>(),

                                code =
                                    response.status.value.toString()

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


                            handleResponsesSseEvent(
                                endpoint,
                                eventLines
                            ) {

                                emit(it)

                            }



                            eventLines.clear()


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
