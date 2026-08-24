package com.vibe.app.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.vibe.app.data.network.NetworkClient
import com.vibe.app.data.network.OpenAIAPI
import com.vibe.app.data.network.OpenAIAPIImpl
import com.vibe.app.data.network.OpenRouterModelsAPI
import com.vibe.app.feature.diagnostic.ChatDiagnosticLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {


    @Provides
    @Singleton
    fun provideNetworkClient(
        @ApplicationContext context: Context
    ): NetworkClient {

        return NetworkClient(

            OkHttp.create {

                addInterceptor(

                    ChuckerInterceptor.Builder(context)
                        .build()

                )

            }

        )

    }



    @Provides
    @Singleton
    fun provideOpenAIAPI(
        networkClient: NetworkClient,
        diagnosticLogger: ChatDiagnosticLogger
    ): OpenAIAPI {

        return OpenAIAPIImpl(

            networkClient = networkClient,

            diagnosticLogger = diagnosticLogger

        )

    }



    @Provides
    @Singleton
    fun provideOpenRouterModelsAPI(): OpenRouterModelsAPI {

        return Retrofit.Builder()
            .baseUrl("https://openrouter.ai/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenRouterModelsAPI::class.java)

    }

}
