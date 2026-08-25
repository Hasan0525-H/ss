package com.vibe.app.feature.agent.loop

import com.vibe.app.feature.agent.AgentLoopRequest
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentLoopEvent
import com.vibe.app.feature.agent.AgentToolDefinition


fun AgentLoopRequest.toModelRequest(
    tools: List<AgentToolDefinition>
): AgentModelRequest {

    return AgentModelRequest(
        platform = platform,
        instructions = systemPrompt,
        tools = tools,
        diagnosticContext = diagnosticContext,
        policy = policy,
        conversation = conversation
    )
}


fun AgentModelEvent.toLoopEvent(): AgentLoopEvent {

    return when (this) {

        is AgentModelEvent.OutputDelta ->
            AgentLoopEvent.OutputDelta(
                delta = delta
            )

        is AgentModelEvent.ThinkingDelta ->
            AgentLoopEvent.ThinkingDelta(
                delta = delta
            )

        is AgentModelEvent.ToolCallReady ->
            AgentLoopEvent.ToolExecutionStarted(
                call = call
            )

        is AgentModelEvent.Completed ->
            AgentLoopEvent.LoopCompleted(
                finalText = ""
            )

        is AgentModelEvent.Failed ->
            AgentLoopEvent.LoopFailed(
                message = message
            )
    }
}
