package com.vibe.app.feature.agent.loop

import com.vibe.app.data.dto.qwen.request.QwenChatCompletionRequest
import com.vibe.app.data.dto.qwen.request.QwenChatMessage
import com.vibe.app.data.dto.qwen.request.QwenFunctionDefinition
import com.vibe.app.data.dto.qwen.request.QwenTool
import com.vibe.app.data.dto.qwen.request.qwenTextContent
import com.vibe.app.data.network.OpenAIAPI
import com.vibe.app.data.preferences.LanguageManager
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
        request: AgentModelRequest
    ): Flow<AgentModelEvent> = flow {


        openAIAPI.setToken(request.platform.token)

        openAIAPI.setAPIUrl(
            request.platform.apiUrl.toQwenChatCompletionsBaseUrl()
        )


        openAIAPI.setProvider(
            type = request.platform.compatibleType.name,
            customUrl = request.platform.apiUrl
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
                    platformUid = request.platform.uid
                )
                ?.let { diagnosticContext ->

                    ModelRequestDiagnosticContext(
                        diagnosticContext = diagnosticContext,
                        providerType =
                            request.platform.compatibleType
                                .toDiagnosticProviderType(),

                        apiFamily = "chat_completions",

                        model = request.platform.model,

                        stream = true,

                        reasoningEnabled =
                            request.platform.reasoning,

                        estimatedContextTokens =
                            request.estimateContextTokensForDiagnostics(),

                        messageCount =
                            messages.size,

                        toolCount =
                            request.tools.size
                                .takeIf { it > 0 },

                        toolChoiceMode =
                            effectiveToolChoice,

                        systemPromptPresent =
                            !request.instructions.isNullOrBlank(),

                        systemPromptChars =
                            request.instructions
                                ?.length
                                ?.takeIf { it > 0 }
                    )
                }



        data class ToolCallAccumulator(
            var id: String = "",
            var name: String = "",
            val arguments: StringBuilder = StringBuilder()
        )


        val toolCallAccumulators =
            mutableMapOf<Int, ToolCallAccumulator>()


        var finishReason: String? = null
        var streamError: String? = null

        var lastAssistantText = ""
        var repeatCount = 0
        var shouldStopFlow = false
        
        // متغير لحفظ الأدوات القادمة في الـ message النهائي كـ Fallback
        var fallbackToolCalls: List<Any>? = null



        openAIAPI.streamQwenChatCompletion(

            QwenChatCompletionRequest(

                model = request.platform.model,

                messages = messages,


                tools =
                    request.tools
                        .takeIf { it.isNotEmpty() }
                        ?.map { tool ->

                            QwenTool(

                                function =
                                    QwenFunctionDefinition(

                                        name = tool.name,

                                        description =
                                            tool.description,

                                        parameters =
                                            tool.inputSchema
                                    )
                            )
                        },


                toolChoice =
                    effectiveToolChoice,


                stream = true
            ),


            diagnosticContext = requestContext,

            trace = trace

        ).collect { chunk ->

            if (shouldStopFlow) return@collect

            if (chunk.error != null) {

                streamError =
                    chunk.error.message


                trace.markFailed(
                    chunk.error.type ?: "provider_error",
                    chunk.error.message
                )


                shouldStopFlow = true
                return@collect
            }



            val choice =
                chunk.choices?.firstOrNull()
                    ?: return@collect



            finishReason =
                choice.finishReason
                    ?: finishReason

            // التقاط أدوات الـ message إذا أرسلتها النماذج في النهاية مباشرة
            if (choice.message?.toolCalls != null && choice.message.toolCalls.isNotEmpty()) {
                fallbackToolCalls = choice.message.toolCalls
            }



            choice.delta?.content
                ?.takeIf { it.isNotEmpty() }
                ?.let { delta ->

                    if (delta == lastAssistantText) {
                        repeatCount++
                    } else {
                        lastAssistantText = delta
                        repeatCount = 0
                    }

                    if (repeatCount >= 3) {
                        emit(
                            AgentModelEvent.Failed("Model repeated the same response multiple times")
                        )
                        shouldStopFlow = true
                        return@let
                    }

                    trace.markOutput(delta)

                    emit(
                        AgentModelEvent.OutputDelta(delta)
                    )
                }

            if (shouldStopFlow) return@collect



            choice.delta?.reasoningContent
                ?.takeIf { it.isNotEmpty() }
                ?.let { delta ->

                    emit(
                        AgentModelEvent.ThinkingDelta(delta)
                    )
                }



            choice.delta?.toolCalls
                ?.forEach { deltaToolCall ->


                    val acc =
                        toolCallAccumulators
                            .getOrPut(
                                deltaToolCall.index
                            ) {
                                ToolCallAccumulator()
                            }


                    deltaToolCall.id
                        ?.let {
                            acc.id = it
                        }


                    deltaToolCall.function?.name
                        ?.let {
                            acc.name = it
                        }


                    deltaToolCall.function?.arguments
                        ?.let {
                            acc.arguments.append(it)
                        }
                }
        }



        streamError?.let {

            if (requestContext != null) {

                diagnosticLogger.logModelResponse(
                    requestContext,
                    trace,
                    success = false
                )


                diagnosticLogger.logLatencyBreakdown(
                    requestContext,
                    trace
                )
            }


            emit(
                AgentModelEvent.Failed(it)
            )


            return@flow
        }


        // المعالجة المزدوجة (دعم الـ Accumulators أو الـ Fallback قادم من الـ message)
        if (toolCallAccumulators.isNotEmpty()) {
            toolCallAccumulators.entries
                .sortedBy { it.key }
                .forEach { (_, acc) ->


                    val arguments =
                        runCatching {

                            json.parseToJsonElement(
                                acc.arguments.toString()
                            )

                        }.getOrElse {


                            buildJsonObject {

                                put(
                                    "raw",
                                    JsonPrimitive(
                                        acc.arguments.toString()
                                    )
                                )
                            }
                        }



                    emit(

                        AgentModelEvent.ToolCallReady(

                            AgentToolCall(

                                id = acc.id,

                                name = acc.name,

                                arguments = arguments
                            )
                        )
                    )
                }
        } else if (!fallbackToolCalls.isNullOrEmpty()) {
            // معالجة الـ ToolCalls الواردة عبر message نهائي في حال لم ترسلها النماذج عبر delta التدريجي
            fallbackToolCalls?.forEach { toolCall ->
                // نفترض توافق البنية للوصول إلى id و function (name & arguments)
                val toolId = (toolCall as? com.vibe.app.data.dto.qwen.request.QwenToolCall)?.id ?: ""
                val func = (toolCall as? com.vibe.app.data.dto.qwen.request.QwenToolCall)?.function
                val name = func?.name ?: ""
                val rawArgs = func?.arguments ?: "{}"

                val arguments = runCatching {
                    json.parseToJsonElement(rawArgs)
                }.getOrElse {
                    buildJsonObject { put("raw", JsonPrimitive(rawArgs)) }
                }

                emit(
                    AgentModelEvent.ToolCallReady(
                        AgentToolCall(
                            id = toolId,
                            name = name,
                            arguments = arguments
                        )
                    )
                )
            }
        }




        trace.finishReason = finishReason

        trace.markCompleted(finishReason)



        if (requestContext != null) {

            diagnosticLogger.logModelResponse(
                requestContext,
                trace,
                success = true
            )


            diagnosticLogger.logLatencyBreakdown(
                requestContext,
                trace
            )
        }



        emit(
            AgentModelEvent.Completed()
        )
    }




    private fun buildMessages(
        request: AgentModelRequest
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
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        append(it)
                    }



                if (toolRequired && hasTools) {

                    append("\n\n")
                    append(
                        toolRequiredInstruction()
                    )

                    append("\n\nIMPORTANT:\nDo not write normal text.\nDo not write explanations.\nStart directly by calling write_project_file.")


                } else if (hasTools) {


                    append("\n\n")
                    append(
                        TOOL_ENCOURAGE_INSTRUCTION
                    )
                }

            }.trim()



        if (systemContent.isNotBlank()) {

            messages += QwenChatMessage(

                role = "system",

                content =
                    qwenTextContent(
                        systemContent
                    )
            )
        }



        request.fullConversation
            .forEach { item ->


                when(item.role) {


                    AgentMessageRole.USER ->

                        messages += QwenChatMessage(

                            role = "user",

                            content =
                                qwenTextContent(
                                    item.text.orEmpty()
                                )
                        )



                    AgentMessageRole.ASSISTANT ->

                        messages += QwenChatMessage(

                            role = "assistant",

                            content =
                                qwenTextContent(
                                    item.text
                                )
                        )



                    AgentMessageRole.TOOL ->

                        messages += QwenChatMessage(

                            role = "tool",

                            content =
                                qwenTextContent(
                                    item.payload?.toString()
                                        ?: item.text.orEmpty()
                                ),

                            toolCallId =
                                item.toolCallId
                        )



                    AgentMessageRole.SYSTEM -> Unit
                }
            }


        return messages
    }


    private fun toolRequiredInstruction(): String {
        return if (languageManager.language.value == "ar") {
            """
أنت وكيل برمجة مستقل.

قواعد اللغة:
- استخدم اللغة العربية في جميع الردود.
- اكتب الشروحات بالعربية.
- اكتب تعليقات الكود بالعربية.
- اجعل رسائل البناء والاختبار بالعربية.

عند طلب إنشاء تطبيق:
- يجب عليك استخدام write_project_file.
- لا تشرح للمستخدم ما يجب فعله.
- نفذ الأدوات بنفسك.
- لا ترسل رد نصي بدون تنفيذ الأدوات.

الرد نصي فقط يعتبر فاشلاً.
"""
        } else {
            """
You are an autonomous coding agent.

Language rules:
- Use English in all responses.
- Write explanations in English.
- Write code comments in English.
- Write build and testing reports in English.

For every application creation request:
- You MUST call write_project_file.
- Do not explain.
- Do not tell the user to use tools.
- Do not output instructions.
- Execute the tools yourself.

A text-only response is invalid.
"""
        }
    }



    companion object {


        private const val TOOL_ENCOURAGE_INSTRUCTION =

            """
## IMPORTANT: Continue Using Tools

You have tools available.
When the user's request requires reading, writing, modifying project files, or building the project, use tools instead of describing what to do.
"""
    }
}




fun String.toQwenChatCompletionsBaseUrl(): String {

    val trimmed =
        this.trim()
            .trimEnd('/')


    return if (trimmed.endsWith("/v1")) {

        trimmed

    } else {

        "$trimmed/v1"
    }
}




fun AgentModelRequest.toQwenToolChoice(): String? {

    if (tools.isEmpty()) {
        return null
    }

    return when (policy.toolChoiceMode) {
        AgentToolChoiceMode.AUTO ->
            "auto"

        AgentToolChoiceMode.REQUIRED ->
            "auto"

        AgentToolChoiceMode.NONE ->
            "none"
    }
}
