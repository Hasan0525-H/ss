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

        return try {

            when(value?.uppercase()) {

                "OPEN_ROUTER",
                "OPENROUTER" ->
                    ClientType.OPEN_ROUTER


                "CUSTOM" ->
                    ClientType.CUSTOM


                else ->
                    ClientType.OPEN_ROUTER

            }

        } catch (
            e: Exception
        ) {

            ClientType.OPEN_ROUTER

        }

    }

}
