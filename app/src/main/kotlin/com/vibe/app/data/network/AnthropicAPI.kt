package com.vibe.app.data.network

interface AnthropicAPI {

    fun setToken(
        token: String?
    )


    fun setAPIUrl(
        url: String
    )


    fun setProvider(
        type: String,
        customUrl: String? = null
    )

}
