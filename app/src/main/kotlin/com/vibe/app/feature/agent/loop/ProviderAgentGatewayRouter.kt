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
    private val anthropicGateway: AnthropicMessagesAgentGateway,
    private val qwenGateway: QwenChatCompletionsAgentGateway,
    private val kimiGateway: KimiChatCompletionsAgentGateway,
    private val deepSeekGateway: DeepSeekChatCompletionsAgentGateway,
) : AgentModelGateway {

    override suspend fun streamTurn(
        request: AgentModelRequest
    ): Flow<AgentModelEvent> {

        return when (request.platform.compatibleType) {

            ClientType.OPENAI ->
                openAiGateway.streamTurn(request)

            ClientType.ANTHROPIC ->
                anthropicGateway.streamTurn(request)

            ClientType.QWEN ->
                qwenGateway.streamTurn(request)

            ClientType.KIMI ->
                kimiGateway.streamTurn(request)

            ClientType.DEEPSEEK ->
                deepSeekGateway.streamTurn(request)

            ClientType.OPEN_ROUTER ->
                qwenGateway.streamTurn(request)

            ClientType.GOOGLE_AI_STUDIO ->
                qwenGateway.streamTurn(request)

            ClientType.CUSTOM ->
                qwenGateway.streamTurn(request)

            ClientType.MINIMAX ->
                qwenGateway.streamTurn(request)
        }
    }
}
