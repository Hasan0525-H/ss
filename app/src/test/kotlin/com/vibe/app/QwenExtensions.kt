package com.vibe.app.data.dto.qwen.request

import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolChoiceMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

fun String.toQwenChatCompletionsBaseUrl(): String {
    val trimmed = this.trim().trimEnd('/')
    return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
}

fun Map<String, JsonElement>.toQwenChatToolSchema(): QwenToolSchema {
    val propertiesObj = this["properties"] as? JsonObject
    val propertiesMap = propertiesObj?.toMap() ?: emptyMap()

    val requiredList = (this["required"] as? List<*>)
        ?.mapNotNull { it?.toString()?.replace("\"", "") }
        ?: emptyList()

    return QwenToolSchema(
        type = "object",
        properties = propertiesMap,
        required = requiredList
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
