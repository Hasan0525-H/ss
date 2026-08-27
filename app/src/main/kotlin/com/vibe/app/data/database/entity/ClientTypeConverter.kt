package com.vibe.app.data.database.entity

import androidx.room.TypeConverter
import com.vibe.app.data.model.ClientType

class ClientTypeConverter {

    @TypeConverter
    fun fromClientType(
        type: ClientType?
    ): String {
        return type?.name
            ?: ClientType.OPEN_ROUTER.name
    }

    @TypeConverter
    fun toClientType(
        value: String?
    ): ClientType {

        val normalized =
            value
                ?.trim()
                ?.uppercase()
                ?: return ClientType.OPEN_ROUTER

        return when (normalized) {

            "OPENROUTER",
            "OPEN_ROUTER" ->
                ClientType.OPEN_ROUTER

            "GOOGLE",
            "GEMINI",
            "GOOGLE_AI_STUDIO" ->
                ClientType.GOOGLE_AI_STUDIO

            else ->
                ClientType.values()
                    .firstOrNull {
                        it.name == normalized
                    }
                    ?: ClientType.OPEN_ROUTER
        }
    }
}
