package com.vibe.app.data.dto.qwen.request

import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolChoiceMode

fun String.toQwenChatCompletionsBaseUrl(): String {
    val trimmed = this.trim().trimEnd('/')
    return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
}

fun Map<String, Any?>.toQwenChatToolSchema(): QwenToolSchema {
    return QwenToolSchema(
        type = "object",
        properties = this["properties"] as? Map<String, Any?> ?: emptyMap(),
        required = this["required"] as? List<String> ?: emptyList()
    )
}

fun AgentModelRequest.toQwenToolChoice(): String? {
    if (this.tools.isEmpty()) return null
    return when (this.policy.toolChoiceMode) {
        AgentToolChoiceMode.AUTO -> "auto"
        AgentToolChoiceMode.REQUIRED -> "required"
        AgentToolChoiceMode.NONE -> "none"
    }
}
