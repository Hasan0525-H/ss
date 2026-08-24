package com.vibe.app.data.dto.qwen.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class QwenToolSchema(
    val type: String = "object",
    val properties: Map<String, Any?> = emptyMap(),
    val required: List<String> = emptyList()
)
