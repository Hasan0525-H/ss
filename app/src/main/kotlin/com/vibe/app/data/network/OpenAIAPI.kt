package com.vibe.app.data.network

import com.vibe.app.data.dto.openai.request.ChatCompletionRequest
import com.vibe.app.data.dto.openai.request.ResponsesRequest
import com.vibe.app.data.dto.openai.response.ChatCompletionChunk
import com.vibe.app.data.dto.openai.response.ResponsesStreamEvent
import com.vibe.app.feature.diagnostic.ModelExecutionTrace
import com.vibe.app.feature.diagnostic.ModelRequestDiagnosticContext
import kotlinx.coroutines.flow.Flow

interface OpenAIAPI {

    /**
     * تحديث مفتاح API للمزود الحالي
     */
    fun setToken(token: String?)


    /**
     * تحديث رابط API المخصص
     * يستخدم مع Custom API
     */
    fun setAPIUrl(url: String)


    /**
     * تحديد نوع المزود
     *
     * OPEN_ROUTER:
     * يستخدم OpenRouter API
     *
     * CUSTOM:
     * يستخدم رابط المستخدم
     */
    fun setProvider(
        type: String,
        customUrl: String? = null
    )


    /**
     * OpenAI compatible Chat Completions API
     * يعمل مع OpenRouter ومعظم المزودات المتوافقة
     */
    fun streamChatCompletion(
        request: ChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext? = null,
        trace: ModelExecutionTrace? = null,
    ): Flow<ChatCompletionChunk>


    /**
     * OpenAI Responses API
     * يعمل مع المزودات التي تدعم Responses endpoint
     */
    fun streamResponses(
        request: ResponsesRequest,
        diagnosticContext: ModelRequestDiagnosticContext? = null,
        trace: ModelExecutionTrace? = null,
    ): Flow<ResponsesStreamEvent>

}
