package com.vibe.app.data.network

import com.vibe.app.data.dto.anthropic.request.MessageRequest
import com.vibe.app.data.dto.anthropic.response.AnthropicResponseChunk
import com.vibe.app.feature.diagnostic.ModelExecutionTrace
import com.vibe.app.feature.diagnostic.ModelRequestDiagnosticContext
import kotlinx.coroutines.flow.Flow

interface AnthropicAPI {

    fun setToken(
        token: String?
    )

    fun setAPIUrl(
        url: String
    )

    fun setProvider(
        type: String,
        customUrl: String? = null
    )

    fun streamChatMessage(
        request: MessageRequest,
        context: ModelRequestDiagnosticContext? = null,
        trace: ModelExecutionTrace
    ): Flow<AnthropicResponseChunk>

}
