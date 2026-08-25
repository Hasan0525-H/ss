package com.vibe.app.feature.agent

import com.vibe.app.feature.agent.tool.AgentToolDefinition

// تحويل طلب حلقة الوكيل إلى طلب نموذج
fun AgentLoopRequest.toModelRequest(tools: List<AgentToolDefinition>): AgentModelRequest {
    return AgentModelRequest(
        platform = platform,
        instructions = systemPrompt,
        tools = tools,
        diagnosticContext = diagnosticContext
    )
}

// تحويل حدث نموذج الوكيل إلى حدث حلقة الوكيل
fun AgentModelEvent.toLoopEvent(): AgentLoopEvent {
    return when (this) {
        is AgentModelEvent.OutputDelta -> AgentLoopEvent.OutputDelta(delta)
        is AgentModelEvent.ThinkingDelta -> AgentLoopEvent.ThinkingDelta(delta)
        is AgentModelEvent.ToolCallReady -> AgentLoopEvent.ToolExecutionStarted(call)
        is AgentModelEvent.Completed -> AgentLoopEvent.LoopCompleted(finalText = "")
        is AgentModelEvent.Failed -> AgentLoopEvent.LoopFailed(message)
    }
}
