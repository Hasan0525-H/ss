package com.vibe.app.feature.agent.loop

import com.vibe.app.data.dto.qwen.request.QwenChatCompletionRequest
import com.vibe.app.data.dto.qwen.request.QwenChatMessage
import com.vibe.app.data.dto.qwen.request.QwenFunctionCall
import com.vibe.app.data.dto.qwen.request.QwenFunctionDefinition
import com.vibe.app.data.dto.qwen.request.QwenTool
import com.vibe.app.data.dto.qwen.request.QwenToolCall
import com.vibe.app.data.dto.qwen.request.qwenTextContent
import com.vibe.app.data.network.OpenAIAPI
import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolCall
import com.vibe.app.feature.agent.AgentToolChoiceMode
import com.vibe.app.feature.diagnostic.ChatDiagnosticLogger
import com.vibe.app.feature.diagnostic.ModelExecutionTrace
import com.vibe.app.feature.diagnostic.ModelRequestDiagnosticContext
import com.vibe.app.feature.diagnostic.toDiagnosticProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class QwenChatCompletionsAgentGateway @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val diagnosticLogger: ChatDiagnosticLogger,
) : AgentModelGateway {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun streamTurn(
        request: AgentModelRequest,
    ): Flow<AgentModelEvent> = flow {

        openAIAPI.setToken(
            request.platform.token
        )

        openAIAPI.setAPIUrl(
            request.platform.apiUrl
                .toQwenChatCompletionsBaseUrl()
        )

        /*
         * Set the provider AFTER setAPIUrl.
         *
         * This is important for Google AI Studio:
         * OpenAIAPIImpl selects the Google endpoint
         * from ClientType.GOOGLE_AI_STUDIO.
         */
        openAIAPI.setProvider(
            type = request.platform.compatibleType.name,
            customUrl = request.platform.apiUrl,
        )

        val trace =
            ModelExecutionTrace()

        val effectiveToolChoice =
            request.toQwenToolChoice()

        val messages =
            buildMessages(request)

        trace.markRequestPrepared()

        val requestContext =
            request.diagnosticContext
                ?.copy(
                    platformUid =
                        request.platform.uid
                )
                ?.let { diagnosticContext ->

                    ModelRequestDiagnosticContext(
                        diagnosticContext =
                            diagnosticContext,

                        providerType =
                            request.platform
                                .compatibleType
                                .toDiagnosticProviderType(),

                        apiFamily =
                            "chat_completions",

                        model =
                            request.platform.model,

                        stream =
                            true,

                        reasoningEnabled =
                            request.platform.reasoning,

                        estimatedContextTokens =
                            request
                                .estimateContextTokensForDiagnostics(),

                        messageCount =
                            messages.size,

                        toolCount =
                            request.tools
                                .size
                                .takeIf {
                                    it > 0
                                },

                        toolChoiceMode =
                            effectiveToolChoice,

                        systemPromptPresent =
                            !request.instructions
                                .isNullOrBlank(),

                        systemPromptChars =
                            request.instructions
                                ?.length
                                ?.takeIf {
                                    it > 0
                                },
                    )
                }

        data class ToolCallAccumulator(
            var id: String = "",
            var name: String = "",
            val arguments: StringBuilder =
                StringBuilder(),
        )

        val toolCallAccumulators =
            mutableMapOf<
                Int,
                ToolCallAccumulator
            >()

        var finishReason: String? =
            null

        var streamError: String? =
            null

        val reasoningBuilder =
            StringBuilder()

        var lastAssistantText =
            ""

        var repeatCount =
            0

        var shouldStopFlow =
            false

        /*
         * Do NOT use an OpenRouter fallback such as:
         *
         *     openrouter/free
         *
         * here.
         *
         * The selected model must be sent directly.
         *
         * This also applies to Google AI Studio:
         * the selected Gemini model is sent directly.
         */

        openAIAPI
            .streamQwenChatCompletion(
                QwenChatCompletionRequest(

                    model =
                        request.platform.model,

                    messages =
                        messages,

                    tools =
                        request.tools
                            .takeIf {
                                it.isNotEmpty()
                            }
                            ?.map { tool ->

                                QwenTool(
                                    type =
                                        "function",

                                    function =
                                        QwenFunctionDefinition(
                                            name =
                                                tool.name,

                                            description =
                                                tool.description,

                                            parameters =
                                                tool.inputSchema,
                                        ),
                                )
                            },

                    toolChoice =
                        effectiveToolChoice,

                    stream =
                        true,
                ),

                diagnosticContext =
                    requestContext,

                trace =
                    trace,
            )
            .collect { chunk ->

                if (shouldStopFlow) {
                    return@collect
                }

                /*
                 * Provider/API error.
                 *
                 * Do not retry here.
                 * The caller receives one Failed event.
                 */
                if (chunk.error != null) {

                    streamError =
                        chunk.error.message

                    trace.markFailed(
                        chunk.error.type
                            ?: "provider_error",

                        chunk.error.message,
                    )

                    shouldStopFlow =
                        true

                    return@collect
                }

                val choice =
                    chunk.choices
                        ?.firstOrNull()
                        ?: return@collect

                finishReason =
                    choice.finishReason
                        ?: finishReason

                val delta =
                    choice.delta

                val message =
                    choice.message

                val content =
                    delta?.content
                        ?: message?.content

                content
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let { text ->

                        if (
                            text ==
                                lastAssistantText
                        ) {

                            repeatCount++

                        } else {

                            lastAssistantText =
                                text

                            repeatCount =
                                0
                        }

                        /*
                         * Protect against a provider
                         * repeatedly streaming the same
                         * content forever.
                         */
                        if (
                            repeatCount >= 3
                        ) {

                            streamError =
                                "Model repeated the same response multiple times"

                            shouldStopFlow =
                                true

                            return@let
                        }

                        trace.markOutput(
                            text
                        )

                        emit(
                            AgentModelEvent.OutputDelta(
                                text
                            )
                        )
                    }

                if (shouldStopFlow) {
                    return@collect
                }

                delta
                    ?.reasoningContent
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let { reasoning ->

                        reasoningBuilder
                            .append(
                                reasoning
                            )

                        emit(
                            AgentModelEvent.ThinkingDelta(
                                reasoning
                            )
                        )
                    }

                val toolCalls =
                    delta?.toolCalls
                        ?: message?.toolCalls

                toolCalls
                    ?.forEach { toolCall ->

                        val toolIndex =
                            toolCall.index

                        val accumulator =
                            toolCallAccumulators
                                .getOrPut(
                                    toolIndex
                                ) {
                                    ToolCallAccumulator()
                                }

                        toolCall.id
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                accumulator.id =
                                    it
                            }

                        toolCall.function
                            ?.name
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                accumulator.name =
                                    it
                            }

                        toolCall.function
                            ?.arguments
                            ?.let {
                                accumulator.arguments
                                    .append(it)
                            }
                    }
            }

        /*
         * If the provider returned an error,
         * emit exactly one AgentModelEvent.Failed
         * and stop this model turn.
         */
        streamError
            ?.let { error ->

                requestContext
                    ?.let { context ->

                        diagnosticLogger
                            .logModelResponse(
                                context,
                                trace,
                                success = false,
                            )

                        diagnosticLogger
                            .logLatencyBreakdown(
                                context,
                                trace,
                            )
                    }

                emit(
                    AgentModelEvent.Failed(
                        error
                    )
                )

                return@flow
            }

        /*
         * Convert accumulated streamed tool calls
         * into AgentToolCall objects.
         */
        toolCallAccumulators
            .entries
            .sortedBy {
                it.key
            }
            .forEach { (_, accumulator) ->

                if (
                    accumulator.name
                        .isBlank()
                ) {
                    return@forEach
                }

                val rawArguments =
                    accumulator.arguments
                        .toString()
                        .trim()

                val arguments =
                    if (
                        rawArguments.isBlank()
                    ) {

                        buildJsonObject {}

                    } else {

                        runCatching {

                            json.parseToJsonElement(
                                rawArguments
                            )

                        }.getOrElse {

                            buildJsonObject {

                                put(
                                    "raw",
                                    JsonPrimitive(
                                        rawArguments
                                    )
                                )
                            }
                        }
                    }

                trace.markToolCall()

                emit(
                    AgentModelEvent.ToolCallReady(

                        AgentToolCall(

                            id =
                                accumulator.id
                                    .ifBlank {
                                        "call_${System.nanoTime()}"
                                    },

                            name =
                                accumulator.name,

                            arguments =
                                arguments,
                        )
                    )
                )
            }

        val reasoningContent =
            reasoningBuilder
                .toString()
                .takeIf {
                    it.isNotBlank()
                }

        reasoningContent
            ?.let {
                trace.markThinking(it)
            }

        trace.finishReason =
            finishReason

        trace.markCompleted(
            finishReason
        )

        requestContext
            ?.let { context ->

                diagnosticLogger
                    .logModelResponse(
                        context,
                        trace,
                        success = true,
                    )

                diagnosticLogger
                    .logLatencyBreakdown(
                        context,
                        trace,
                    )
            }

        emit(
            AgentModelEvent.Completed(
                reasoningContent =
                    reasoningContent
            )
        )
    }

    private fun buildMessages(
        request: AgentModelRequest,
    ): List<QwenChatMessage> {

        val messages =
            mutableListOf<QwenChatMessage>()

        val toolRequired =
            request.policy.toolChoiceMode ==
                AgentToolChoiceMode.REQUIRED

        val hasTools =
            request.tools.isNotEmpty()

        val systemContent =
            buildString {

                request.instructions
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        append(it)
                    }

                if (
                    toolRequired &&
                    hasTools
                ) {

                    append(
                        "\n\n"
                    )

                    append(
                        TOOL_REQUIRED_INSTRUCTION
                    )

                } else if (
                    hasTools
                ) {

                    append(
                        "\n\n"
                    )

                    append(
                        TOOL_ENCOURAGE_INSTRUCTION
                    )
                }

            }.trim()

        if (
            systemContent.isNotBlank()
        ) {

            messages +=
                QwenChatMessage(

                    role =
                        "system",

                    content =
                        qwenTextContent(
                            systemContent
                        ),
                )
        }

        /*
         * Use the complete conversation here.
         *
         * OpenAI-compatible Chat Completions
         * endpoints are stateless, so every request
         * must contain the accumulated conversation.
         *
         * This applies to Google AI Studio as well.
         */
        request.fullConversation
            .forEach { item ->

                when (
                    item.role
                ) {

                    AgentMessageRole.USER ->

                        messages +=
                            QwenChatMessage(

                                role =
                                    "user",

                                content =
                                    qwenTextContent(
                                        item.text
                                            .orEmpty()
                                    ),
                            )

                    AgentMessageRole.ASSISTANT ->

                        messages +=
                            QwenChatMessage(

                                role =
                                    "assistant",

                                content =
                                    qwenTextContent(
                                        item.text
                                    ),

                                toolCalls =
                                    item.toolCalls
                                        ?.map { toolCall ->

                                            QwenToolCall(

                                                id =
                                                    toolCall.id,

                                                function =
                                                    QwenFunctionCall(

                                                        name =
                                                            toolCall.name,

                                                        arguments =
                                                            toolCall
                                                                .arguments
                                                                .toString(),
                                                    ),
                                            )
                                        }
                                        ?.takeIf {
                                            it.isNotEmpty()
                                        },
                            )

                    AgentMessageRole.TOOL ->

                        messages +=
                            QwenChatMessage(

                                role =
                                    "tool",

                                content =
                                    qwenTextContent(

                                        item.payload
                                            ?.toString()
                                            ?: item.text
                                                .orEmpty()
                                    ),

                                toolCallId =
                                    item.toolCallId,
                            )

                    AgentMessageRole.SYSTEM ->
                        Unit
                }
            }

        return messages
    }

    companion object {

        private const val TOOL_REQUIRED_INSTRUCTION =
            """
## MANDATORY TOOL USE
You MUST call at least one tool in your response.
Do NOT reply with only text.
Analyze the user's request and use the appropriate tools to fulfill it.
Every response MUST include one or more tool calls.
A text-only answer is NOT acceptable.
"""

        private const val TOOL_ENCOURAGE_INSTRUCTION =
            """
## IMPORTANT: Continue Using Tools
You have tools available.
When the user's request requires reading, writing, or modifying project files,
or building the project, you MUST use the appropriate tools instead of
describing what to do in text.

Do NOT assume you already know the file contents.
Always use tools to read and write files.
"""
    }
}

internal fun AgentModelRequest.toQwenToolChoice(): String? {

    if (
        tools.isEmpty()
    ) {
        return null
    }

    return when (
        policy.toolChoiceMode
    ) {

        AgentToolChoiceMode.NONE ->
            "none"

        AgentToolChoiceMode.AUTO,
        AgentToolChoiceMode.REQUIRED ->
            "auto"
    }
}

/*
 * Converts provider URLs to the base URL expected by
 * the OpenAI-compatible Qwen/Google gateway.
 *
 * Google AI Studio already uses:
 *
 * https://generativelanguage.googleapis.com/v1beta/openai
 *
 * so it must NOT receive an additional /v1 segment.
 */
private fun String.toQwenChatCompletionsBaseUrl(): String {

    val trimmed =
        trimEnd('/')

    return when {

        "/api/v2/apps/protocols/compatible-mode" in
            trimmed ->

            trimmed.replace(
                "/api/v2/apps/protocols/compatible-mode",
                "/compatible-mode/v1"
            )

        trimmed.endsWith(
            "/compatible-mode/v1"
        ) ->

            trimmed

        else ->

            trimmed
    }
}
