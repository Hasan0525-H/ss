package com.vibe.app.feature.agent.loop

import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ProviderAgentGatewayRouter @Inject constructor(
    private val qwenGateway: QwenChatCompletionsAgentGateway,
) : AgentModelGateway {

    override suspend fun streamTurn(
        request: AgentModelRequest
    ): Flow<AgentModelEvent> {

        return when (
            request.platform.compatibleType
        ) {

            /*
             * Current supported providers.
             *
             * All three use the OpenAI-compatible
             * Chat Completions implementation.
             */
            ClientType.OPEN_ROUTER ->
                qwenGateway.streamTurn(request)

            ClientType.GOOGLE_AI_STUDIO ->
                qwenGateway.streamTurn(request)

            ClientType.CUSTOM ->
                qwenGateway.streamTurn(request)

            /*
             * Legacy ClientType values remain in the enum
             * for Room/database compatibility.
             *
             * They are no longer exposed in setup UI.
             * Routing them through the compatible gateway
             * also prevents unused legacy gateways from
             * forcing Hilt dependencies such as AnthropicAPI.
             */
            ClientType.OPENAI,
            ClientType.ANTHROPIC,
            ClientType.QWEN,
            ClientType.KIMI,
            ClientType.MINIMAX,
            ClientType.DEEPSEEK ->
                qwenGateway.streamTurn(request)
        }
    }
}
