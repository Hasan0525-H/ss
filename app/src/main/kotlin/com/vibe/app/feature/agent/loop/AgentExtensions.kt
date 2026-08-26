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
        diagnosticContext = diagnosticContext,

        conversation = emptyList(),

        fullConversation = emptyList(),

        instructions = systemPrompt,

        tools = tools,

        policy = policy
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
                call = call,
                iteration = 0
            )


        is AgentModelEvent.Completed ->
            AgentLoopEvent.LoopCompleted(
                finalText = finalText ?: "",
                iteration = 0
            )


        is AgentModelEvent.Failed ->
            AgentLoopEvent.LoopFailed(
                message = message,
                iteration = 0
            )
    }
}
