package com.vibe.app.di

import com.vibe.app.feature.agent.AgentLoopCoordinator
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.loop.DefaultAgentLoopCoordinator
import com.vibe.app.feature.agent.loop.QwenChatCompletionsAgentGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindAgentLoopCoordinator(
        coordinator: DefaultAgentLoopCoordinator
    ): AgentLoopCoordinator

    @Binds
    @Singleton
    abstract fun bindAgentModelGateway(
        gateway: QwenChatCompletionsAgentGateway
    ): AgentModelGateway
}
