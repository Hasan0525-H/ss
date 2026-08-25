package com.vibe.app.feature.agent

import com.vibe.app.feature.agent.AgentToolDefinition

// تحويل طلب حلقة الوكيل إلى طلب نموذج
fun AgentLoopRequest.toModelRequest(tools: List<AgentToolDefinition>): AgentModelRequest {
    return AgentModelRequest(
        platform = platform,
        userMessages = userMessages,
        assistantMessages = assistantMessages,
        instructions = systemPrompt,
        tools = tools,
        diagnosticContext = diagnosticContext,
        policy = AgentModelPolicy()
    )
}

// تحويل حدث نموذج الوكيل إلى حدث حلقة الوكيل
fun AgentModelEvent.toLoopEvent(): AgentLoopEvent {
    return when (this) {
        is AgentModelEvent.OutputDelta -> AgentLoopEvent.OutputDelta(text)
        is AgentModelEvent.ThinkingDelta -> AgentLoopEvent.ThinkingDelta(thought)
        is AgentModelEvent.ToolCallReady -> AgentLoopEvent.ToolExecutionStarted(call)
        is AgentModelEvent.Completed -> AgentLoopEvent.LoopCompleted(finalText = "")
        is AgentModelEvent.Failed -> AgentLoopEvent.LoopFailed(message)
    }
}
