package com.vibe.app.feature.agent.loop

import com.vibe.app.feature.agent.AgentLoopCoordinator
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentToolRegistry
import com.vibe.app.feature.agent.AgentLoopRequest
import com.vibe.app.feature.agent.AgentLoopEvent
import com.vibe.app.feature.agent.toModelRequest // تأكد من استيراد دوال الامتداد بشكل صحيح
import com.vibe.app.feature.agent.toLoopEvent  // تأكد من استيراد دوال الامتداد بشكل صحيح
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAgentLoopCoordinator @Inject constructor(
    private val modelGateway: AgentModelGateway,
    private val toolRegistry: AgentToolRegistry
) : AgentLoopCoordinator {

    override suspend fun run(
        request: AgentLoopRequest
    ): Flow<AgentLoopEvent> = flow {

        val modelRequest = request.toModelRequest(
            tools = toolRegistry.listDefinitions()
        )

        modelGateway
            .streamTurn(modelRequest)
            .collect { event ->
                emit(
                    event.toLoopEvent()
                )
            }
    }
}
