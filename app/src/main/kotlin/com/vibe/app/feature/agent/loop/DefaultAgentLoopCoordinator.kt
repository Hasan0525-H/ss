package com.vibe.app.feature.agent.loop

import com.vibe.app.feature.agent.AgentLoopCoordinator
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentToolRegistry
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentModelEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAgentLoopCoordinator @Inject constructor(
    private val modelGateway: AgentModelGateway,
    private val toolRegistry: AgentToolRegistry
) : AgentLoopCoordinator {

    override suspend fun run(request: AgentModelRequest): Flow<AgentModelEvent> {
        // تنفيذ دورة الوكيل وإدارة الاستجابات والأدوات
        return modelGateway.streamTurn(request)
    }
}
