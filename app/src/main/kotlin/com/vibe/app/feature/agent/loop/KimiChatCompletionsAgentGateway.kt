package com.vibe.app.feature.agent.loop

import com.vibe.app.data.dto.qwen.request.QwenToolSchema

fun String.toKimiBaseUrl(): String {
    val trimmed = this.trim().trimEnd('/')
    return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
}

fun Map<String, Any?>.toKimiToolSchema(): QwenToolSchema {
    return QwenToolSchema(
        type = "object",
        properties = this["properties"] as? Map<String, Any?> ?: emptyMap(),
        required = this["required"] as? List<String> ?: emptyList()
    )
}
