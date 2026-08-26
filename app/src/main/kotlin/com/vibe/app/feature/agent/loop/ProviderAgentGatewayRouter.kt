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
             * Chat Completions API in this project.
             *
             * This is the important route for the
             * current problem.
             */
            ClientType.OPEN_ROUTER ->
                qwenGateway.streamTurn(request)

            /*
             * Custom providers in this fork use the
             * OpenAI-compatible Chat Completions path.
             */
            ClientType.CUSTOM ->
                qwenGateway.streamTurn(request)

            /*
             * Keep the Responses gateway as the fallback
             * for provider types supported by it.
             */
            ClientType.ANTHROPIC,
            ClientType.MINIMAX,
            ClientType.DEEPSEEK ->
                openAiGateway.streamTurn(request)
        }
    }
}
