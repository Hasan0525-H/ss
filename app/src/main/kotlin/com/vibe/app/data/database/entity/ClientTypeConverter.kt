package com.vibe.app.data.database.entity

import androidx.room.TypeConverter
import com.vibe.app.data.model.ClientType

class ClientTypeConverter {

    @TypeConverter
    fun fromClientType(type: ClientType): String {
        return type.name
    }


    @TypeConverter
    fun toClientType(value: String): ClientType {
        return try {
            ClientType.valueOf(value)
        } catch (e: Exception) {
            ClientType.OPEN_ROUTER
        }
    }
}
