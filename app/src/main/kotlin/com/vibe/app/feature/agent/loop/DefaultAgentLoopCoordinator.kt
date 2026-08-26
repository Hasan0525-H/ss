package com.vibe.app.feature.agent.loop

import com.vibe.app.feature.agent.AgentLoopCoordinator
import com.vibe.app.feature.agent.AgentLoopEvent
import com.vibe.app.feature.agent.AgentLoopRequest
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentToolRegistry
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolDefinition
// تم حذف السطر اللي كان يسبب الخطأ من هنا

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAgentLoopCoordinator @Inject constructor(
    private val modelGateway: AgentModelGateway,
    private val toolRegistry: AgentToolRegistry,
) : AgentLoopCoordinator {

    override suspend fun run(
        request: AgentLoopRequest
    ): Flow<AgentLoopEvent> = flow {

        val modelRequest = request.toModelRequest(
            tools = toolRegistry.listDefinitions()
        )

        var iteration = 0

        emit(
            AgentLoopEvent.LoopStarted(
                chatId = request.chatId,
                platformUid = request.platform.uid,
            )
        )

        iteration++

        emit(
            AgentLoopEvent.ModelTurnStarted(
                iteration = iteration
            )
        )

        modelGateway
            .streamTurn(modelRequest)
            .collect { event ->

                emit(
                    event.toLoopEvent(
                        iteration = iteration
                    )
                )
            }
    }
}

// --- أضفنا هذه الدالة هنا في الأسفل لحل مشكلة Unresolved reference ---
private fun AgentLoopRequest.toModelRequest(tools: List<AgentToolDefinition>): AgentModelRequest {
    return AgentModelRequest(
        platform = this.platform,
        tools = tools,
        
        // تنبيه: ستحتاج إلى استبدال هذه القيم المؤقتة بالبيانات الصحيحة الموجودة في AgentLoopRequest
        conversation = emptyList(), // TODO: عدلها لتأخذ المحادثة الفعلية
        fullConversation = emptyList(), // TODO: عدلها لتأخذ المحادثة الكاملة
        // policy = this.policy // TODO: أزل علامة التعليق إذا كانت Policy موجودة في الـ Request
        policy = throw NotImplementedError("يجب تمرير الـ policy هنا")
    )
}
