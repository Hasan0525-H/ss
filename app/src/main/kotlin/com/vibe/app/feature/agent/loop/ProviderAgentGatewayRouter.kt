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
    private val openAiGateway: OpenAiResponsesAgentGateway,
    private val qwenGateway: QwenChatCompletionsAgentGateway,
    private val kimiGateway: KimiChatCompletionsAgentGateway,
) : AgentModelGateway {

    override suspend fun streamTurn(
        request: AgentModelRequest
    ): Flow<AgentModelEvent> {

        return when (request.platform.compatibleType) {

            ClientType.QWEN ->
                qwenGateway.streamTurn(request)

            ClientType.KIMI ->
                kimiGateway.streamTurn(request)

            ClientType.OPENAI ->
                openAiGateway.streamTurn(request)

            /*
             * OpenRouter uses the OpenAI-compatible
             * Chat Completions API.
             */
            ClientType.OPEN_ROUTER ->
                qwenGateway.streamTurn(request)

            /*
             * Google AI Studio uses Google's official
             * OpenAI-compatible Gemini endpoint.
             *
             * The API URL and API key are taken from
             * the selected Google AI Studio platform.
             */
            ClientType.GOOGLE_AI_STUDIO ->
                qwenGateway.streamTurn(request)

            /*
             * Custom providers use the OpenAI-compatible
             * Chat Completions path.
             */
            ClientType.CUSTOM ->
                qwenGateway.streamTurn(request)

            /*
             * Providers that use the Responses gateway.
             */
            ClientType.ANTHROPIC,
            ClientType.MINIMAX,
            ClientType.DEEPSEEK ->
                openAiGateway.streamTurn(request)
        }
    }
}
