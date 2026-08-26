package com.vibe.app.feature.agent.loop

import com.vibe.app.data.dto.qwen.request.QwenChatCompletionRequest
import com.vibe.app.data.dto.qwen.request.QwenChatMessage
import com.vibe.app.data.dto.qwen.request.QwenFunctionCall
import com.vibe.app.data.dto.qwen.request.QwenFunctionDefinition
import com.vibe.app.data.dto.qwen.request.QwenTool
import com.vibe.app.data.dto.qwen.request.QwenToolCall
import com.vibe.app.data.dto.qwen.request.qwenTextContent
import com.vibe.app.data.network.OpenAIAPI
import com.vibe.app.data.preferences.LanguageManager
import com.vibe.app.feature.agent.AgentConversationItem
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Singleton
class QwenChatCompletionsAgentGateway @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val diagnosticLogger: ChatDiagnosticLogger,
    private val languageManager: LanguageManager,
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

        openAIAPI.setProvider(
            type = request.platform.compatibleType.name,
            customUrl = request.platform.apiUrl,
        )

        val trace = ModelExecutionTrace()

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

                        stream = true,

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
                                .takeIf { it > 0 },

                        toolChoiceMode =
                            effectiveToolChoice,

                        systemPromptPresent =
                            !request.instructions
                                .isNullOrBlank(),

                        systemPromptChars =
                            request.instructions
                                ?.length
                                ?.takeIf { it > 0 },
                    )
                }

        data class ToolCallAccumulator(
            var id: String = "",
            var name: String = "",
            val arguments: StringBuilder =
                StringBuilder(),
        )

        val toolCallAccumulators =
            mutableMapOf<Int, ToolCallAccumulator>()

        var finishReason: String? = null
        var streamError: String? = null

        var reasoningBuilder =
            StringBuilder()

        var lastAssistantText = ""
        var repeatCount = 0
        var shouldStopFlow = false

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
                                    type = "function",

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

                    /*
                     * IMPORTANT:
                     *
                     * When the agent says REQUIRED,
                     * send "required" to the provider.
                     *
                     * The old code converted REQUIRED
                     * into "auto", which allowed the model
                     * to answer with text without calling
                     * write_project_file.
                     */
                    toolChoice =
                        effectiveToolChoice,

                    stream = true,
                ),

                diagnosticContext =
                    requestContext,

                trace = trace,

            )
            .collect { chunk ->

                if (shouldStopFlow) {
                    return@collect
                }

                if (chunk.error != null) {

                    streamError =
                        chunk.error.message

                    trace.markFailed(
                        chunk.error.type
                            ?: "provider_error",

                        chunk.error.message,
                    )

                    shouldStopFlow = true

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

                /*
                 * Normal streamed text.
                 */
                val content =
                    delta?.content
                        ?: message?.content

                content
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let { text ->

                        if (text == lastAssistantText) {
                            repeatCount++
                        } else {
                            lastAssistantText =
                                text
                            repeatCount = 0
                        }

                        if (repeatCount >= 3) {

                            streamError =
                                "Model repeated the same response multiple times"

                            shouldStopFlow = true

                            return@let
                        }

                        trace.markOutput(text)

                        emit(
                            AgentModelEvent.OutputDelta(
                                text
                            )
                        )
                    }

                if (shouldStopFlow) {
                    return@collect
                }

                /*
                 * Reasoning / thinking.
                 */
                delta
                    ?.reasoningContent
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                    ?.let { reasoning ->

                        reasoningBuilder
                            .append(reasoning)

                        emit(
                            AgentModelEvent.ThinkingDelta(
                                reasoning
                            )
                        )
                    }

                /*
                 * IMPORTANT:
                 *
                 * Tool calls can arrive in several
                 * streaming chunks.
                 *
                 * Accumulate them until the stream ends.
                 */
                val toolCalls =
                    delta?.toolCalls
                        ?: message?.toolCalls

                toolCalls
                    ?.forEachIndexed { index, toolCall ->

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
                                accumulator.id = it
                            }

                        toolCall.function
                            ?.name
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                accumulator.name = it
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
         * Provider error.
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
         * Convert accumulated tool calls
         * into AgentModelEvent.ToolCallReady.
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
                    if (rawArguments.isBlank()) {

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
                }
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

                    append("\n\n")

                    append(
                        toolRequiredInstruction()
                    )

                    append(
                        "\n\n" +
                            "IMPORTANT:\n" +
                            "For an application creation request, " +
                            "you MUST call write_project_file. " +
                            "Do not answer with normal text first. " +
                            "Start by using the available tools."
                    )

                } else if (hasTools) {

                    append("\n\n")

                    append(
                        TOOL_ENCOURAGE_INSTRUCTION
                    )
                }
            }
                .trim()

        if (
            systemContent.isNotBlank()
        ) {

            messages +=
                QwenChatMessage(

                    role = "system",

                    content =
                        qwenTextContent(
                            systemContent
                        ),
                )
        }

        /*
         * Rebuild the complete conversation.
         *
         * IMPORTANT:
         * Preserve assistant tool_calls.
         * The old implementation dropped them.
         */
        request.fullConversation
            .forEach { item ->

                when (item.role) {

                    AgentMessageRole.USER -> {

                        messages +=
                            QwenChatMessage(

                                role = "user",

                                content =
                                    qwenTextContent(
                                        item.text.orEmpty()
                                    ),
                            )
                    }

                    AgentMessageRole.ASSISTANT -> {

                        messages +=
                            QwenChatMessage(

                                role = "assistant",

                                content =
                                    qwenTextContent(
                                        item.text
                                    ),

                                reasoningContent =
                                    item.reasoningContent,

                                toolCalls =
                                    item.toolCalls
                                        ?.map { toolCall ->

                                            QwenToolCall(

                                                id =
                                                    toolCall.id,

                                                type =
                                                    "function",

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
                    }

                    AgentMessageRole.TOOL -> {

                        messages +=
                            QwenChatMessage(

                                role = "tool",

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

                    AgentMessageRole.SYSTEM -> {
                        // System instructions are handled above.
                    }
                }
            }

        return messages
    }

    private fun toolRequiredInstruction(): String {

        return if (
            languageManager.language.value == "ar"
        ) {

            """
            أنت وكيل برمجة مستقل.

            عند طلب إنشاء تطبيق:
            - يجب عليك استخدام أدوات المشروع.
            - يجب عليك استدعاء write_project_file.
            - لا تكتفِ بشرح أو إعطاء كود للمستخدم.
            - نفذ الأدوات بنفسك.
            - ابدأ باستدعاء الأداة المناسبة مباشرة.
            - الرد النصي بدون تنفيذ الأدوات يعتبر فاشلاً.
            """.trimIndent()

        } else {

            """
            You are an autonomous coding agent.

            For every application creation request:
            - You MUST use the project tools.
            - You MUST call write_project_file.
            - Do not merely explain or provide code to the user.
            - Execute the tools yourself.
            - Start by calling the appropriate tool directly.
            - A text-only response without tool execution is invalid.
            """.trimIndent()
        }
    }

    companion object {

        private const val TOOL_ENCOURAGE_INSTRUCTION =
            """
            ## IMPORTANT: Continue Using Tools

            You have project tools available.

            When the user's request requires:
            - creating files
            - modifying files
            - deleting files
            - reading files
            - building the project

            use the tools instead of describing what to do.
            """.trimIndent()
    }
}

fun String.toQwenChatCompletionsBaseUrl(): String {

    val trimmed =
        this
            .trim()
            .trimEnd('/')

    return if (
        trimmed.endsWith("/v1")
    ) {

        trimmed

    } else {

        "$trimmed/v1"
    }
}

/*
 * IMPORTANT FIX:
 *
 * REQUIRED must remain REQUIRED.
 *
 * The previous implementation converted:
 *
 *     REQUIRED -> "auto"
 *
 * which allowed Qwen/OpenRouter to return a
 * normal text response without calling tools.
 */
fun AgentModelRequest.toQwenToolChoice(): String? {

    if (
        tools.isEmpty()
    ) {
        return null
    }

    return when (
        policy.toolChoiceMode
    ) {

        AgentToolChoiceMode.AUTO ->
            "auto"

        AgentToolChoiceMode.REQUIRED ->
            "required"

        AgentToolChoiceMode.NONE ->
            "none"
    }
}
