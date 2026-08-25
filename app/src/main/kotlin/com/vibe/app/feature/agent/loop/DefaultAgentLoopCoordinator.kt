package com.vibe.app.feature.agent.loop

import com.vibe.app.feature.agent.AgentLoopCoordinator
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentToolRegistry
import com.vibe.app.feature.agent.AgentLoopRequest
import com.vibe.app.feature.agent.AgentLoopEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAgentLoopCoordinator @Inject constructor(
    private val modelGateway: AgentModelGateway,
    private val toolRegistry: AgentToolRegistry
) : AgentLoopCoordinator {

    override suspend fun run(request: AgentLoopRequest): Flow<AgentLoopEvent> = flow {
        // يمكنك هنا تحويل AgentLoopRequest إلى الطلب المناسب للـ modelGateway
        // واستخدام toolRegistry عند الحاجة لتنفيذ الأدوات، ثم إصدار الأحداث عبر emit
    }
}
