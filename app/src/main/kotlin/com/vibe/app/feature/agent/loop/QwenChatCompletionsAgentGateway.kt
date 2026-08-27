package com.vibe.app.feature.agent.loop

import com.vibe.app.data.dto.qwen.request.QwenChatCompletionRequest
import com.vibe.app.data.dto.qwen.request.QwenChatMessage
import com.vibe.app.data.dto.qwen.request.QwenFunctionCall
import com.vibe.app.data.dto.qwen.request.QwenFunctionDefinition
import com.vibe.app.data.dto.qwen.request.QwenTool
import com.vibe.app.data.dto.qwen.request.QwenToolCall
import com.vibe.app.data.dto.qwen.request.qwenTextContent
import com.vibe.app.data.model.ClientType
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

    private val json =
        Json {
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

        openAIAPI.setProvider(
            type =
                request.platform.compatibleType.name,
            customUrl =
                request.platform.apiUrl,
        )

        /*
         * NONE:
         * Do not send tools.
         *
         * AUTO:
         * Send tools initially.
         *
         * REQUIRED:
         * Send tools and never fall back to text-only.
         */
        var includeTools =
            request.shouldSendTools()

        /*
         * OpenRouter AUTO fallback is allowed once only.
         */
        var toolFallbackAttempted =
            false

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

        /*
         * These are replaced for each attempt.
         *
         * This matters because a text-only retry must have
         * its own clean diagnostics trace.
         */
        var trace =
            ModelExecutionTrace()

        var requestContext:
            ModelRequestDiagnosticContext? =
            null

        /*
         * Maximum effective attempts:
         *
         * 1) Normal request
         * 2) Optional OpenRouter AUTO retry without tools
         */
        while (true) {

            toolCallAccumulators.clear()

            finishReason =
                null

            streamError =
                null

            reasoningBuilder.clear()

            lastAssistantText =
                ""

            repeatCount =
                0

            trace =
                ModelExecutionTrace()

            trace.markRequestPrepared()

            val messages =
                buildMessages(
                    request =
                        request,
                    includeTools =
                        includeTools,
                )

            val effectiveToolChoice =
                request.toQwenToolChoice(
                    includeTools =
                        includeTools
                )

            requestContext =
                buildDiagnosticContext(
                    request =
                        request,
                    messages =
                        messages,
                    includeTools =
                        includeTools,
                    effectiveToolChoice =
                        effectiveToolChoice,
                )

            val qwenTools =
                buildQwenTools(
                    request =
                        request,
                    includeTools =
                        includeTools,
                )

            /*
             * Tracks whether this attempt already produced
             * anything useful.
             *
             * We only retry without tools if the provider
             * rejected tools before generating output.
             */
            var attemptProducedContent =
                false

            var attemptSawToolCall =
                false

            var shouldStopAttempt =
                false

            var retryWithoutTools =
                false

            openAIAPI
                .streamQwenChatCompletion(
                    request =
                        QwenChatCompletionRequest(

                            /*
                             * Always keep the exact model
                             * selected by the user.
                             */
                            model =
                                request.platform.model,

                            messages =
                                messages,

                            tools =
                                qwenTools,

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

                    if (
                        shouldStopAttempt
                    ) {
                        return@collect
                    }

                    val providerError =
                        chunk.error

                    if (
                        providerError != null
                    ) {

                        val errorMessage =
                            providerError.message

                        /*
                         * Important:
                         *
                         * OpenRouter can reject the request
                         * before model execution when the chosen
                         * model has no endpoint supporting tools.
                         *
                         * For AUTO mode only, retry exactly once
                         * without tools.
                         *
                         * REQUIRED mode intentionally does not
                         * fall back because tools are mandatory.
                         */
                        val canRetryWithoutTools =
                            request.platform.compatibleType ==
                                ClientType.OPEN_ROUTER &&
                                includeTools &&
                                !toolFallbackAttempted &&
                                request.policy.toolChoiceMode ==
                                AgentToolChoiceMode.AUTO &&
                                !attemptProducedContent &&
                                !attemptSawToolCall &&
                                errorMessage
                                    .isUnsupportedToolError()

                        if (
                            canRetryWithoutTools
                        ) {

                            retryWithoutTools =
                                true

                            shouldStopAttempt =
                                true

                            return@collect
                        }

                        streamError =
                            errorMessage

                        trace.markFailed(
                            providerError.type
                                ?: "provider_error",

                            errorMessage,
                        )

                        shouldStopAttempt =
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

                            attemptProducedContent =
                                true

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
                             * Protect against providers that
                             * repeatedly stream identical chunks.
                             */
                            if (
                                repeatCount >= 3
                            ) {

                                val error =
                                    "Model repeated the same response multiple times"

                                streamError =
                                    error

                                trace.markFailed(
                                    "repeated_response",
                                    error,
                                )

                                shouldStopAttempt =
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

                    if (
                        shouldStopAttempt
                    ) {
                        return@collect
                    }

                    delta
                        ?.reasoningContent
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?.let { reasoning ->

                            attemptProducedContent =
                                true

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

                            attemptSawToolCall =
                                true

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
                                        .append(
                                            it
                                        )
                                }
                        }
                }

            /*
             * OpenRouter rejected tools for this model.
             *
             * Retry the SAME model once without tools.
             */
            if (
                retryWithoutTools
            ) {

                toolFallbackAttempted =
                    true

                includeTools =
                    false

                continue
            }

            /*
             * Either request succeeded or failed normally.
             * Do not perform another attempt.
             */
            break
        }

        /*
         * Final provider failure.
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
         * Convert streamed tool calls into AgentToolCall.
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
                trace.markThinking(
                    it
                )
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

    private fun buildDiagnosticContext(
        request: AgentModelRequest,
        messages: List<QwenChatMessage>,
        includeTools: Boolean,
        effectiveToolChoice: String?,
    ): ModelRequestDiagnosticContext? {

        return request.diagnosticContext
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
                        if (
                            includeTools
                        ) {

                            request.tools
                                .size
                                .takeIf {
                                    it > 0
                                }

                        } else {

                            null
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
    }

    private fun buildQwenTools(
        request: AgentModelRequest,
        includeTools: Boolean,
    ): List<QwenTool>? {

        if (
            !includeTools ||
            request.tools.isEmpty()
        ) {
            return null
        }

        return request.tools
            .map { tool ->

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
            }
    }

    private fun buildMessages(
        request: AgentModelRequest,
        includeTools: Boolean,
    ): List<QwenChatMessage> {

        val messages =
            mutableListOf<QwenChatMessage>()

        val toolRequired =
            includeTools &&
                request.policy.toolChoiceMode ==
                AgentToolChoiceMode.REQUIRED

        val hasTools =
            includeTools &&
                request.tools.isNotEmpty()

        val systemContent =
            buildString {

                request.instructions
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        append(
                            it
                        )
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
         * Chat Completions is stateless.
         *
         * Send the complete conversation every request.
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

                    AgentMessageRole.ASSISTANT -> {

                        /*
                         * During text-only fallback we remove
                         * historical tool_calls because a model
                         * without tool support may reject them.
                         */
                        val historicalToolCalls =
                            if (
                                includeTools
                            ) {

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
                                    }

                            } else {

                                null
                            }

                        /*
                         * If this assistant message contained
                         * only a tool call and no text, omit it
                         * from a text-only fallback.
                         */
                        val hasAssistantText =
                            !item.text
                                .isNullOrBlank()

                        if (
                            includeTools ||
                            hasAssistantText
                        ) {

                            messages +=
                                QwenChatMessage(
                                    role =
                                        "assistant",

                                    content =
                                        qwenTextContent(
                                            item.text
                                        ),

                                    toolCalls =
                                        historicalToolCalls,
                                )
                        }
                    }

                    AgentMessageRole.TOOL -> {

                        /*
                         * Tool-role messages must not be sent to
                         * a model that rejected tool support.
                         */
                        if (
                            includeTools
                        ) {

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
                        }
                    }

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

/*
 * Should tools actually be sent to the provider?
 */
private fun AgentModelRequest.shouldSendTools(): Boolean {

    if (
        tools.isEmpty()
    ) {
        return false
    }

    return policy.toolChoiceMode !=
        AgentToolChoiceMode.NONE
}

/*
 * Kept with the original no-argument signature
 * in case existing tests/code use it.
 */
internal fun AgentModelRequest.toQwenToolChoice(): String? {

    return toQwenToolChoice(
        includeTools =
            true
    )
}

internal fun AgentModelRequest.toQwenToolChoice(
    includeTools: Boolean,
): String? {

    if (
        !includeTools ||
        tools.isEmpty()
    ) {
        return null
    }

    return when (
        policy.toolChoiceMode
    ) {

        AgentToolChoiceMode.NONE ->
            "none"

        /*
         * Some compatible providers do not accept
         * "required" consistently.
         *
         * REQUIRED is enforced by the system instruction.
         */
        AgentToolChoiceMode.AUTO,
        AgentToolChoiceMode.REQUIRED ->
            "auto"
    }
}

/*
 * Detect only actual tool-support compatibility failures.
 *
 * Do not retry authentication, quota, network or generic
 * model errors as text-only requests.
 */
private fun String.isUnsupportedToolError(): Boolean {

    val normalized =
        lowercase()

    return normalized.contains(
        "no endpoints found that support tool use"
    ) ||
        normalized.contains(
            "no endpoint found that supports tool use"
        ) ||
        normalized.contains(
            "does not support tool use"
        ) ||
        normalized.contains(
            "doesn't support tool use"
        ) ||
        normalized.contains(
            "tool use is not supported"
        ) ||
        normalized.contains(
            "tool calling is not supported"
        ) ||
        normalized.contains(
            "tools are not supported"
        ) ||
        normalized.contains(
            "function calling is not supported"
        )
}

/*
 * Converts provider URLs to the base URL expected by
 * the OpenAI-compatible gateway.
 *
 * Google AI Studio already uses:
 *
 * https://generativelanguage.googleapis.com/v1beta/openai
 *
 * Therefore it must NOT receive another /v1.
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
