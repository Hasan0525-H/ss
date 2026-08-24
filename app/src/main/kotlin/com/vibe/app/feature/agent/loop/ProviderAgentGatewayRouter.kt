package com.vibe.app.feature.agent.loop

import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Routes agent model requests based on the selected platform type.
 *
 * OPEN_ROUTER:
 * Uses OpenAI compatible Responses API.
 *
 * CUSTOM:
 * Uses OpenAI compatible custom endpoint.
 */
@Singleton
class ProviderAgentGatewayRouter @Inject constructor(
    private val openAiGateway: OpenAiResponsesAgentGateway,
) : AgentModelGateway {

    override suspend fun streamTurn(
        request: AgentModelRequest
    ): Flow<AgentModelEvent> {
        return when (request.platform.compatibleType) {
            ClientType.OPEN_ROUTER ->
                openAiGateway.streamTurn(request)

            ClientType.CUSTOM ->
                openAiGateway.streamTurn(request)

            // تمت إضافة فرع else لضمان اكتمال عبارة when وتغطية بقية المزودين
            else ->
                openAiGateway.streamTurn(request)
        }
    }
}
